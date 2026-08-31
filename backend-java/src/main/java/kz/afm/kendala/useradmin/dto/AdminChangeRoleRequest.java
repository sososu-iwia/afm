package kz.afm.kendala.useradmin.dto;

import jakarta.validation.constraints.NotNull;
import kz.afm.kendala.application.enums.UserRole;

public record AdminChangeRoleRequest(@NotNull UserRole role) {
}
