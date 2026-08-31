package kz.afm.kendala.useradmin;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;
import kz.afm.kendala.common.CurrentUserProvider;
import kz.afm.kendala.common.dto.PageResponse;
import kz.afm.kendala.useradmin.dto.AdminChangeRoleRequest;
import kz.afm.kendala.useradmin.dto.AdminSessionResponse;
import kz.afm.kendala.useradmin.dto.AdminUserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Administration")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {
    private final AdminUserService service;
    private final CurrentUserProvider currentUserProvider;

    public AdminUserController(AdminUserService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) UserAccountStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.list(currentUserProvider.getCurrentUser(), role, status, search, page - 1, size);
    }

    @GetMapping("/{userId}")
    public AdminUserResponse get(@PathVariable UUID userId) {
        return service.get(userId);
    }

    @PatchMapping("/{userId}/block")
    public AdminUserResponse block(@PathVariable UUID userId) {
        return service.block(currentUserProvider.getCurrentUser(), userId);
    }

    @PatchMapping("/{userId}/unblock")
    public AdminUserResponse unblock(@PathVariable UUID userId) {
        return service.unblock(currentUserProvider.getCurrentUser(), userId);
    }

    @PatchMapping("/{userId}/disable")
    public AdminUserResponse disable(@PathVariable UUID userId) {
        return service.disable(currentUserProvider.getCurrentUser(), userId);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserResponse changeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminChangeRoleRequest request
    ) {
        return service.changeRole(currentUserProvider.getCurrentUser(), userId, request.role());
    }

    @GetMapping("/{userId}/sessions")
    public List<AdminSessionResponse> sessions(@PathVariable UUID userId) {
        return service.sessions(userId);
    }

    @DeleteMapping("/{userId}/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@PathVariable UUID userId) {
        service.revokeSessions(currentUserProvider.getCurrentUser(), userId);
    }

    @DeleteMapping("/{userId}/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID userId, @PathVariable UUID sessionId) {
        service.revokeSession(currentUserProvider.getCurrentUser(), userId, sessionId);
    }
}
