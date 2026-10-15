package com.example.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.taskmanager.dto.PageResponse;
import com.example.taskmanager.dto.TaskResponse;
import com.example.taskmanager.enums.TaskPriority;
import com.example.taskmanager.enums.TaskStatus;
import com.example.taskmanager.exception.InvalidRequestException;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TaskService taskService;

    @Test
    void listQueryUsesOnlyTheAuthenticatedOwnersId() {
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(42L);
        when(taskRepository.searchOwnedTasks(eq(42L), isNull(), eq(TaskStatus.PENDING),
                eq(TaskPriority.HIGH), any(Pageable.class))).thenReturn(Page.empty());

        PageResponse<TaskResponse> result = taskService.getTasks(
                principal, null, TaskStatus.PENDING, TaskPriority.HIGH, 0, 20, "dueDate", "ASC"
        );

        assertThat(result.content()).isEmpty();
        verify(taskRepository).searchOwnedTasks(eq(42L), isNull(), eq(TaskStatus.PENDING),
                eq(TaskPriority.HIGH), any(Pageable.class));
    }

    @Test
    void rejectsUnsafeSortFieldBeforeCallingRepository() {
        UserPrincipal principal = mock(UserPrincipal.class);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> taskService.getTasks(
                        principal, null, null, null, 0, 20, "user.password", "ASC"))
                .isInstanceOf(InvalidRequestException.class);

        verify(taskRepository, never()).searchOwnedTasks(any(), any(), any(), any(), any());
    }
}
