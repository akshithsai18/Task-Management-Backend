package com.example.taskmanager.dto;

import com.example.taskmanager.repository.TaskStatisticsProjection;

public record TaskStatisticsResponse(
        long totalTasks,
        long pendingTasks,
        long inProgressTasks,
        long completedTasks,
        long overdueTasks,
        double completionPercentage
) {
    public static TaskStatisticsResponse from(TaskStatisticsProjection statistics) {
        long total = valueOrZero(statistics.getTotalTasks());
        long completed = valueOrZero(statistics.getCompletedTasks());
        double percentage = total == 0 ? 0.0 : Math.round((completed * 10000.0) / total) / 100.0;
        return new TaskStatisticsResponse(
                total,
                valueOrZero(statistics.getPendingTasks()),
                valueOrZero(statistics.getInProgressTasks()),
                completed,
                valueOrZero(statistics.getOverdueTasks()),
                percentage
        );
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
