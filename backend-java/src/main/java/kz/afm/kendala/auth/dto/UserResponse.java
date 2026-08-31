package kz.afm.kendala.auth.dto;

import java.util.UUID;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.application.enums.UserRole;

public record UserResponse(UUID id, String phone, String fullName, String email, UserRole role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getPhone(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
