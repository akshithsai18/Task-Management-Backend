package com.example.taskmanager.repository;

import com.example.taskmanager.entity.Task;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select t from Task t
            where t.user.id = :userId
              and (:search is null or lower(t.title) like lower(concat('%', :search, '%'))
                   or lower(coalesce(t.description, '')) like lower(concat('%', :search, '%')))
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            """)
    Page<Task> searchOwnedTasks(
            @Param("userId") Long userId,
            @Param("search") String search,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            Pageable pageable
    );

    Optional<Task> findByIdAndUser_Id(Long id, Long userId);

    @Query("""
            select count(t) as totalTasks,
                   coalesce(sum(case when t.status = :pending then 1 else 0 end), 0) as pendingTasks,
                   coalesce(sum(case when t.status = :inProgress then 1 else 0 end), 0) as inProgressTasks,
                   coalesce(sum(case when t.status = :completed then 1 else 0 end), 0) as completedTasks,
                   coalesce(sum(case when t.dueDate < :today and t.status <> :completed then 1 else 0 end), 0) as overdueTasks
            from Task t
            where t.user.id = :userId
            """)
    TaskStatisticsProjection getStatistics(
            @Param("userId") Long userId,
            @Param("today") LocalDate today,
            @Param("pending") TaskStatus pending,
            @Param("inProgress") TaskStatus inProgress,
            @Param("completed") TaskStatus completed
    );
}
