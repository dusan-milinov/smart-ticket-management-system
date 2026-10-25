package com.ticketmanagement.backend.dto.auth;

import com.ticketmanagement.backend.model.AppUser;

/** Administrator view of an account; includes activation state, never credentials. */
public record AdminUserResponse(
        Long id,
        String email,
        String role,
        boolean active
) {
    public static AdminUserResponse from(AppUser user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isActive()
        );
    }
}
