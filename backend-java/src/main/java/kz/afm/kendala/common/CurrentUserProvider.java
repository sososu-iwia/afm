package kz.afm.kendala.common;

import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.common.exception.NotFoundException;
import kz.afm.kendala.common.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Требуется аутентификация");
        }
        UUID userId = UUID.fromString(auth.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (!user.isActive()) {
            throw new UnauthorizedException("Пользователь неактивен или не подтверждён");
        }
        return user;
    }
}
