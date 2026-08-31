package kz.afm.kendala.useradmin;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.audit.AuditService;
import kz.afm.kendala.auth.entity.RefreshToken;
import kz.afm.kendala.auth.repository.RefreshTokenRepository;
import kz.afm.kendala.common.PaginationPolicy;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.common.exception.BusinessRuleException;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.useradmin.dto.AdminSessionResponse;
import kz.afm.kendala.useradmin.dto.AdminUserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminUserService {
    private static final EnumSet<UserRole> ASSIGNABLE_ROLES = EnumSet.allOf(UserRole.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public AdminUserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> list(
            User actor,
            UserRole role,
            UserAccountStatus status,
            String search,
            int page,
            int size
    ) {
        var spec = (org.springframework.data.jpa.domain.Specification<User>) (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("accountStatus"), status));
            }
            if (StringUtils.hasText(search)) {
                String term = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), term),
                        cb.like(cb.lower(root.get("email")), term)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        int boundedSize = Math.min(Math.max(size, 1), 100);
        var result = userRepository.findAll(spec, PageRequest.of(
                PaginationPolicy.zeroBased(page),
                boundedSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))
        ));
        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID userId) {
        return toResponse(findUser(userId));
    }

    @Transactional
    public AdminUserResponse block(User actor, UUID userId) {
        return changeStatus(actor, userId, UserAccountStatus.BLOCKED, "USER_BLOCKED", "ADMIN_BLOCK");
    }

    @Transactional
    public AdminUserResponse unblock(User actor, UUID userId) {
        return changeStatus(actor, userId, UserAccountStatus.ACTIVE, "USER_UNBLOCKED", "ADMIN_UNBLOCK");
    }

    @Transactional
    public AdminUserResponse disable(User actor, UUID userId) {
        return changeStatus(actor, userId, UserAccountStatus.DISABLED, "USER_DISABLED", "ADMIN_DISABLE");
    }

    @Transactional
    public AdminUserResponse changeRole(User actor, UUID userId, UserRole newRole) {
        if (!ASSIGNABLE_ROLES.contains(newRole)) {
            throw new BusinessRuleException("Роль недоступна для назначения");
        }
        User target = findUser(userId);
        if (actor.getId().equals(target.getId())) {
            throw new BusinessRuleException("Нельзя изменить собственную роль");
        }
        UserRole oldRole = target.getRole();
        if (oldRole == newRole) {
            return toResponse(target);
        }
        target.setRole(newRole);
        refreshTokenRepository.revokeAllForUser(target.getId(), "ROLE_CHANGED");
        auditService.recordDomain(
                actor,
                "USER_ROLE_CHANGED",
                "USER",
                target.getId().toString(),
                java.util.Map.of("oldRole", oldRole),
                java.util.Map.of("newRole", newRole)
        );
        return toResponse(target);
    }

    @Transactional(readOnly = true)
    public List<AdminSessionResponse> sessions(UUID userId) {
        findUser(userId);
        return refreshTokenRepository.findActiveSessionsByUserId(userId)
                .stream()
                .map(this::toSession)
                .toList();
    }

    @Transactional
    public void revokeSessions(User actor, UUID userId) {
        User target = findUser(userId);
        refreshTokenRepository.revokeAllForUser(target.getId(), "ADMIN_REVOKE");
        auditService.recordDomain(
                actor,
                "USER_SESSIONS_REVOKED",
                "USER",
                target.getId().toString(),
                null,
                java.util.Map.of("scope", "all")
        );
    }

    @Transactional
    public void revokeSession(User actor, UUID userId, UUID sessionId) {
        User target = findUser(userId);
        int updated = refreshTokenRepository.revokeUserSession(target.getId(), sessionId, "ADMIN_REVOKE");
        if (updated == 0) {
            throw new NotFoundException("Сессия не найдена");
        }
        auditService.recordDomain(
                actor,
                "USER_SESSION_REVOKED",
                "USER",
                target.getId().toString(),
                null,
                java.util.Map.of("sessionId", sessionId)
        );
    }

    private AdminUserResponse changeStatus(
            User actor,
            UUID userId,
            UserAccountStatus newStatus,
            String action,
            String revokeReason
    ) {
        User target = findUser(userId);
        if (actor.getId().equals(target.getId()) && newStatus != UserAccountStatus.ACTIVE) {
            throw new BusinessRuleException("Нельзя заблокировать или отключить собственную учётную запись");
        }
        UserAccountStatus oldStatus = target.getAccountStatus();
        if (oldStatus == newStatus) {
            return toResponse(target);
        }
        target.setAccountStatus(newStatus);
        if (newStatus == UserAccountStatus.ACTIVE && target.getVerifiedAt() == null) {
            target.setVerifiedAt(java.time.Instant.now());
        }
        if (newStatus != UserAccountStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(target.getId(), revokeReason);
        }
        auditService.recordDomain(
                actor,
                action,
                "USER",
                target.getId().toString(),
                java.util.Map.of("oldStatus", oldStatus),
                java.util.Map.of("newStatus", newStatus)
        );
        return toResponse(target);
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                maskPhone(user.getPhone()),
                user.getFullName(),
                maskEmail(user.getEmail()),
                user.getRole(),
                user.getAccountStatus(),
                user.isVerified(),
                user.getVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private AdminSessionResponse toSession(RefreshToken token) {
        return new AdminSessionResponse(
                token.getId(),
                token.getFamilyId(),
                token.getIssuedAt(),
                token.getExpiresAt(),
                token.getLastUsedAt(),
                token.getRevokedAt(),
                token.getRevokeReason(),
                token.getUserAgent(),
                token.getIpAddress()
        );
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() <= 4) {
            return "****";
        }
        return "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
