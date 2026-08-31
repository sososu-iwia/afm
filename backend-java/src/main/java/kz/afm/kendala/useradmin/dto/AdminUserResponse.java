package kz.afm.kendala.useradmin.dto;

import java.time.Instant;
import java.util.UUID;
import kz.afm.kendala.application.enums.UserAccountStatus;
import kz.afm.kendala.application.enums.UserRole;

public record AdminUserResponse(
        UUID id,
        String phoneMasked,
        String fullName,
        String emailMasked,
        UserRole role,
        UserAccountStatus accountStatus,
        boolean verified,
        Instant verifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
