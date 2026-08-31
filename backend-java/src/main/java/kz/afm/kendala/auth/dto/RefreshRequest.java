package kz.afm.kendala.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(
        @NotBlank(message = "{validation.required}")
        @Size(max = 1024, message = "{validation.max-length}")
        String refreshToken
) {}
