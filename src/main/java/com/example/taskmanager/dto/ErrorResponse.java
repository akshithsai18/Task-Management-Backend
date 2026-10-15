package com.example.taskmanager.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        Map<String, String> fieldErrors,
        String path
) {
    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, Map.of(), path);
    }

    public static ErrorResponse validation(int status, String errorCode, String message,
                                           Map<String, String> fieldErrors, String path) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, fieldErrors, path);
    }
}
