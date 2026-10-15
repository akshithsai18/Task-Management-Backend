package com.example.taskmanager.dto;

import java.time.LocalDateTime;

import com.example.taskmanager.entity.User;

public record UserResponse(Long id, String fullName, String email, LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getCreatedAt());
    }
}
