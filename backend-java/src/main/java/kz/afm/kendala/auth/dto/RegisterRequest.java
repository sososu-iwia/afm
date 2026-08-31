package kz.afm.kendala.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @NotBlank(message = "{validation.required}")
        @Schema(example = "+77000000001")
        @Size(max = 32, message = "{validation.max-length}") String phone,
        @NotBlank(message = "{validation.required}")
        @Size(max = 255, message = "{validation.max-length}") String fullName,
        @Email(message = "{validation.email}")
        @Size(max = 320, message = "{validation.max-length}") String email
) {}
