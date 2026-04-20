package com.leadrush.task.service;

import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.repository.SequenceRepository;
import com.leadrush.task.dto.CreateTaskRequest;
import com.leadrush.task.dto.TaskResponse;
import com.leadrush.task.entity.Task;
import com.leadrush.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final ContactRepository contactRepository;
    private final SequenceRepository sequenceRepository;

    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(String status, Pageable pageable) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Page<Task> page;
        if (status != null && !status.isBlank()) {
            page = taskRepository.findByWorkspaceIdAndStatus(
                    workspaceId, Task.TaskStatus.valueOf(status), pageable);
        } else {
            page = taskRepository.findByWorkspaceId(workspaceId, pageable);
        }

        return page.map(this::toResponse);
    }

    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .taskType(request.getTaskType() != null
                        ? Task.TaskType.valueOf(request.getTaskType())
                        : Task.TaskType.MANUAL)
                .contactId(request.getContactId())
                .dueAt(request.getDueAt())
                .status(Task.TaskStatus.PENDING)
                .assignedToUserId(TenantContext.getUserId())
                .build();
        task.setWorkspaceId(workspaceId);

        task = taskRepository.save(task);
        log.info("Task created: {} (id: {})", task.getTitle(), task.getId());
        return toResponse(task);
    }

    @Transactional
    public TaskResponse completeTask(UUID id) {
        Task task = findTask(id);
        task.setStatus(Task.TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task = taskRepository.save(task);
        return toResponse(task);
    }

    @Transactional
    public void deleteTask(UUID id) {
        Task task = findTask(id);
        taskRepository.delete(task);
    }

    /**
     * Create a task triggered by a sequence step.
     * Called by EnrollmentService when a TASK/CALL/LINKEDIN_* step executes.
     */
    @Transactional
    public Task createFromSequenceStep(
            UUID workspaceId,
            UUID contactId,
            UUID sequenceId,
            UUID enrollmentId,
            String description,
            Task.TaskType type
    ) {
        Task task = Task.builder()
                .title(typeLabel(type) + " this contact")
                .description(description)
                .taskType(type)
                .contactId(contactId)
                .sequenceId(sequenceId)
                .enrollmentId(enrollmentId)
                .dueAt(LocalDateTime.now())
                .status(Task.TaskStatus.PENDING)
                .build();
        task.setWorkspaceId(workspaceId);
        return taskRepository.save(task);
    }

    private String typeLabel(Task.TaskType type) {
        return switch (type) {
            case CALL -> "Call";
            case LINKEDIN_MESSAGE -> "Send LinkedIn message to";
            case LINKEDIN_CONNECT -> "Send LinkedIn connection request to";
            case MANUAL -> "Follow up with";
            case NOTE -> "Note on";
        };
    }

    private Task findTask(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return taskRepository.findById(id)
                .filter(t -> t.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private TaskResponse toResponse(Task task) {
        TaskResponse.TaskResponseBuilder b = TaskResponse.builder()
                .id(task.getId())
                .assignedToUserId(task.getAssignedToUserId())
                .title(task.getTitle())
                .description(task.getDescription())
                .taskType(task.getTaskType().name())
                .contactId(task.getContactId())
                .sequenceId(task.getSequenceId())
                .dueAt(task.getDueAt())
                .status(task.getStatus().name())
                .completedAt(task.getCompletedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());

        // Enrich with contact/sequence names (for UI display)
        if (task.getContactId() != null) {
            contactRepository.findById(task.getContactId())
                    .ifPresent(c -> b.contactFullName(c.getFullName()));
        }
        if (task.getSequenceId() != null) {
            sequenceRepository.findById(task.getSequenceId())
                    .ifPresent(s -> b.sequenceName(s.getName()));
        }
        return b.build();
    }
}
