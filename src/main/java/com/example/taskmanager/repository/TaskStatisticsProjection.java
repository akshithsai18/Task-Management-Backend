package com.example.taskmanager.repository;

public interface TaskStatisticsProjection {
    Long getTotalTasks();
    Long getPendingTasks();
    Long getInProgressTasks();
    Long getCompletedTasks();
    Long getOverdueTasks();
}
