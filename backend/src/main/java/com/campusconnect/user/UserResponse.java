package com.campusconnect.user;

import java.time.Instant;

public record UserResponse(Long id, String fullName, String email, Role role, boolean active, Instant createdAt) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive(), user.getCreatedAt());
    }
}
