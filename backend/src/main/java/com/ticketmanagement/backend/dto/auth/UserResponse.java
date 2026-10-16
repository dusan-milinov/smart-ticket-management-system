package com.ticketmanagement.backend.dto.auth;

import com.ticketmanagement.backend.model.AppUser;

public record UserResponse (
        Long id,
        String email,
        String role
)
{
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );
    }
}
