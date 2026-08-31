package kz.afm.kendala.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {}
