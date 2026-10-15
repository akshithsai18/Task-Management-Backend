package com.example.taskmanager.service;

import com.example.taskmanager.dto.PageResponse;
import com.example.taskmanager.dto.TaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskStatisticsResponse;
import com.example.taskmanager.entity.Task;
import com.example.taskmanager.entity.User;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import com.example.taskmanager.exception.InvalidRequestException;
import com.example.taskmanager.exception.ResourceNotFoundException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.TaskStatisticsProjection;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.security.UserPrincipal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Set<String> SORT_FIELDS = Set.of(
            "title", "priority", "status", "dueDate", "createdAt", "updatedAt"
    );

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TaskResponse create(UserPrincipal principal, TaskRequest request) {
        User owner = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User"));
        Task task = new Task(
                request.title().trim(), cleanDescription(request.description()), request.priority(),
                request.status(), request.dueDate(), owner
        );
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> getTasks(UserPrincipal principal, String search, TaskStatus status,
                                                TaskPriority priority, int page, int size,
                                                String sortBy, String direction) {
        if (page < 0) {
            throw new InvalidRequestException("Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("Page size must be between 1 and 100.");
        }
        if (!SORT_FIELDS.contains(sortBy)) {
            throw new InvalidRequestException("sortBy must be one of: " + String.join(", ", SORT_FIELDS) + ".");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("direction must be ASC or DESC.");
        }

        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<Task> tasks = taskRepository.searchOwnedTasks(
                principal.getId(), normalizedSearch, status, priority, pageable
        );
        return PageResponse.from(tasks, tasks.map(TaskResponse::from).getContent());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UserPrincipal principal, Long taskId) {
        return TaskResponse.from(findOwnedTask(principal, taskId));
    }

    @Transactional
    public TaskResponse update(UserPrincipal principal, Long taskId, TaskRequest request) {
        Task task = findOwnedTask(principal, taskId);
        task.setTitle(request.title().trim());
        task.setDescription(cleanDescription(request.description()));
        task.setPriority(request.priority());
        task.setStatus(request.status());
        task.setDueDate(request.dueDate());
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateStatus(UserPrincipal principal, Long taskId, TaskStatus status) {
        Task task = findOwnedTask(principal, taskId);
        task.setStatus(status);
        return TaskResponse.from(taskRepository.save(task));
    }

    @Transactional
    public void delete(UserPrincipal principal, Long taskId) {
        taskRepository.delete(findOwnedTask(principal, taskId));
    }

    @Transactional(readOnly = true)
    public TaskStatisticsResponse statistics(UserPrincipal principal) {
        TaskStatisticsProjection result = taskRepository.getStatistics(
                principal.getId(), LocalDate.now(ZoneOffset.UTC), TaskStatus.PENDING,
                TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED
        );
        return TaskStatisticsResponse.from(result);
    }

    private Task findOwnedTask(UserPrincipal principal, Long taskId) {
        return taskRepository.findByIdAndUser_Id(taskId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Task"));
    }

    private String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
