package com.example.taskmanager.controller;

import com.example.taskmanager.dto.PageResponse;
import com.example.taskmanager.dto.TaskRequest;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.dto.TaskStatisticsResponse;
import com.example.taskmanager.dto.TaskStatusRequest;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import com.example.taskmanager.security.UserPrincipal;
import com.example.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody TaskRequest request) {
        TaskResponse created = taskService.create(principal, request);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<TaskResponse> getTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueDate") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction
    ) {
        return taskService.getTasks(principal, search, status, priority, page, size, sortBy, direction);
    }

    @GetMapping("/statistics")
    public TaskStatisticsResponse statistics(@AuthenticationPrincipal UserPrincipal principal) {
        return taskService.statistics(principal);
    }

    @GetMapping("/{id}")
    public TaskResponse getTask(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return taskService.getTask(principal, id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                               @Valid @RequestBody TaskRequest request) {
        return taskService.update(principal, id, request);
    }

    @PatchMapping("/{id}/status")
    public TaskResponse updateStatus(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
                                     @Valid @RequestBody TaskStatusRequest request) {
        return taskService.updateStatus(principal, id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        taskService.delete(principal, id);
    }
}
