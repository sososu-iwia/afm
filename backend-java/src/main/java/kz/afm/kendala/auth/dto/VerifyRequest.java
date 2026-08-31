package kz.afm.kendala.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kz.afm.kendala.auth.enums.OtpPurpose;
import io.swagger.v3.oas.annotations.media.Schema;

public record VerifyRequest(
        @NotBlank(message = "{validation.required}")
        @Schema(example = "+77000000001")
        @Size(max = 32, message = "{validation.max-length}") String phone,
        @NotBlank(message = "{validation.required}")
        @Schema(description = "Шестизначный OTP из настроенного SMS provider")
        @Pattern(regexp = "\\d{6}", message = "{validation.otp-code}") String code,
        @NotNull(message = "{validation.required}") OtpPurpose purpose
) {}
