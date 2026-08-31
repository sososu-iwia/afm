package kz.afm.kendala.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kz.afm.kendala.common.CurrentUserProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationOutboxRepository repository;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(
            NotificationOutboxRepository repository,
            CurrentUserProvider currentUserProvider
    ) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    @Operation(summary = "Уведомления текущего пользователя")
    @PreAuthorize("isAuthenticated()")
    public List<NotificationResponse> myNotifications() {
        // Выборка строго по владельцу: чужие уведомления недоступны в принципе.
        return repository
                .findTop100ByUserIdOrderByCreatedAtDesc(currentUserProvider.getCurrentUser().getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
