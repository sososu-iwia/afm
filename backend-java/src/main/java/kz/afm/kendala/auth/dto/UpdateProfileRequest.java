package kz.afm.kendala.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "{validation.required}")
        @Size(max = 255, message = "{validation.max-length}")
        @Schema(example = "Томирис Жусупова")
        String fullName,

        @Email(message = "{validation.email}")
        @Size(max = 255, message = "{validation.max-length}")
        @Schema(example = "user@example.kz", description = "Необязательное поле; пустая строка очищает адрес")
        String email
) {}
