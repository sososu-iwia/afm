package kz.afm.kendala.auth.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import kz.afm.kendala.auth.dto.AuthResponse;
import kz.afm.kendala.auth.dto.LoginRequest;
import kz.afm.kendala.auth.dto.LogoutRequest;
import kz.afm.kendala.auth.dto.RefreshRequest;
import kz.afm.kendala.auth.dto.RegisterRequest;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.auth.dto.UpdateProfileRequest;
import kz.afm.kendala.auth.dto.UserResponse;
import kz.afm.kendala.auth.dto.VerifyRequest;
import kz.afm.kendala.auth.service.AuthService;
import kz.afm.kendala.common.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/register")
    @Operation(summary = "Зарегистрировать заявителя и отправить OTP")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        authService.register(request.phone(), request.fullName(), request.email(), clientIp(httpRequest));
    }

    @PostMapping("/login")
    @Operation(summary = "Запросить OTP для входа")
    public ResponseEntity<Map<String, String>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        authService.login(request.phone(), clientIp(httpRequest));
        return ResponseEntity.accepted()
                .body(Map.of("message", "Если номер зарегистрирован, код подтверждения будет отправлен"));
    }

    @PostMapping("/verify")
    @Operation(summary = "Проверить OTP и получить access/refresh tokens")
    public AuthResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletRequest httpRequest) {
        return authService.verify(
                request.phone(),
                request.code(),
                request.purpose(),
                httpRequest.getHeader("User-Agent"),
                clientIp(httpRequest)
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновить JWT-сессию")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(
                request.refreshToken(),
                httpRequest.getHeader("User-Agent"),
                clientIp(httpRequest)
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "Завершить refresh-сессию")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Завершить все сессии пользователя")
    @SecurityRequirement(name = "bearerAuth")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void logoutAll() {
        authService.logoutAll(currentUserProvider.getCurrentUser());
    }

    @GetMapping("/me")
    @Operation(summary = "Получить текущего пользователя")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me() {
        return UserResponse.from(currentUserProvider.getCurrentUser());
    }

    @PatchMapping("/me")
    @Operation(summary = "Обновить профиль текущего пользователя")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("isAuthenticated()")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        User updated = authService.updateProfile(
                currentUserProvider.getCurrentUser(), request.fullName(), request.email());
        return UserResponse.from(updated);
    }

    private String clientIp(HttpServletRequest request) {
        // Proxy headers are intentionally ignored unless a trusted proxy is configured
        // at the servlet container/load-balancer boundary.
        return request.getRemoteAddr();
    }
}
