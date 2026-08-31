package kz.afm.kendala.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @NotBlank(message = "{validation.required}")
        @Schema(example = "+77000000001")
        @Size(max = 32, message = "{validation.max-length}")
        String phone
) {}
