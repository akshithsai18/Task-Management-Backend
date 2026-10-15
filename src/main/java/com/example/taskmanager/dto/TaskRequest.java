package com.example.taskmanager.dto;

import java.time.LocalDate;

import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TaskRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 150, message = "Title must be at most 150 characters.")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters.")
        String description,

        @NotNull(message = "Priority is required.")
        TaskPriority priority,

        @NotNull(message = "Status is required.")
        TaskStatus status,

        LocalDate dueDate
) {
}
