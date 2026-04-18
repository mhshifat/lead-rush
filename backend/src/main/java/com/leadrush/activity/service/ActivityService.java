package com.leadrush.activity.service;

import com.leadrush.activity.dto.ActivityEvent;
import com.leadrush.email.entity.EmailSendLog;
import com.leadrush.email.repository.EmailSendLogRepository;
import com.leadrush.email.tracking.UnsubscribeRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.entity.SequenceStepExecution;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceStepExecutionRepository;
import com.leadrush.task.entity.Task;
import com.leadrush.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Builds a unified activity timeline for a contact.
 *
 * We DERIVE events from existing records (send logs, enrollments, step executions,
 * unsubscribes, tasks) rather than storing separate activity rows.
 * This keeps the schema simple and avoids denormalization.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final EmailSendLogRepository sendLogRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final SequenceStepExecutionRepository executionRepository;
    private final UnsubscribeRepository unsubscribeRepository;
    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<ActivityEvent> getContactTimeline(UUID contactId) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        List<ActivityEvent> events = new ArrayList<>();

        // 1. Sequence enrollments
        List<SequenceEnrollment> enrollments = enrollmentRepository.findByContactId(contactId).stream()
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .toList();

        for (SequenceEnrollment e : enrollments) {
            events.add(ActivityEvent.builder()
                    .id(e.getId())
                    .type("ENROLLED")
                    .title("Enrolled in sequence")
                    .description(e.getSequence().getName())
                    .sequenceId(e.getSequence().getId())
                    .sequenceName(e.getSequence().getName())
                    .occurredAt(e.getEnrolledAt())
                    .build());

            if (e.getCompletedAt() != null) {
                events.add(ActivityEvent.builder()
                        .id(e.getId())
                        .type("SEQUENCE_COMPLETED")
                        .title("Sequence completed")
                        .description(e.getSequence().getName())
                        .sequenceId(e.getSequence().getId())
                        .sequenceName(e.getSequence().getName())
                        .occurredAt(e.getCompletedAt())
                        .build());
            }

            // Derive events from step executions
            List<SequenceStepExecution> execs = executionRepository
                    .findByEnrollmentIdOrderByCreatedAtAsc(e.getId());
            for (SequenceStepExecution exec : execs) {
                if (exec.getSentAt() != null) {
                    events.add(ActivityEvent.builder()
                            .id(exec.getId())
                            .type("EMAIL_SENT")
                            .title("Email sent")
                            .description(e.getSequence().getName())
                            .sequenceId(e.getSequence().getId())
                            .sequenceName(e.getSequence().getName())
                            .stepExecutionId(exec.getId())
                            .occurredAt(exec.getSentAt())
                            .build());
                }
                if (exec.getOpenedAt() != null) {
                    events.add(ActivityEvent.builder()
                            .id(exec.getId())
                            .type("EMAIL_OPENED")
                            .title("Email opened")
                            .description(e.getSequence().getName())
                            .sequenceId(e.getSequence().getId())
                            .sequenceName(e.getSequence().getName())
                            .stepExecutionId(exec.getId())
                            .occurredAt(exec.getOpenedAt())
                            .build());
                }
                if (exec.getFirstClickedAt() != null) {
                    events.add(ActivityEvent.builder()
                            .id(exec.getId())
                            .type("EMAIL_CLICKED")
                            .title("Email link clicked")
                            .description(e.getSequence().getName())
                            .sequenceId(e.getSequence().getId())
                            .sequenceName(e.getSequence().getName())
                            .stepExecutionId(exec.getId())
                            .occurredAt(exec.getFirstClickedAt())
                            .build());
                }
                if (exec.getRepliedAt() != null) {
                    events.add(ActivityEvent.builder()
                            .id(exec.getId())
                            .type("EMAIL_REPLIED")
                            .title("Replied to email")
                            .description(e.getSequence().getName())
                            .sequenceId(e.getSequence().getId())
                            .sequenceName(e.getSequence().getName())
                            .stepExecutionId(exec.getId())
                            .occurredAt(exec.getRepliedAt())
                            .build());
                }
            }
        }

        // 2. Unsubscribe
        unsubscribeRepository.findByWorkspaceIdAndContactId(workspaceId, contactId).ifPresent(u ->
            events.add(ActivityEvent.builder()
                    .id(u.getId())
                    .type("UNSUBSCRIBED")
                    .title("Unsubscribed")
                    .description("Source: " + u.getSource().name())
                    .occurredAt(u.getCreatedAt())
                    .build())
        );

        // 3. Tasks
        List<Task> tasks = taskRepository.findByWorkspaceIdAndContactId(workspaceId, contactId);
        for (Task t : tasks) {
            events.add(ActivityEvent.builder()
                    .id(t.getId())
                    .type("TASK_CREATED")
                    .title("Task created: " + t.getTitle())
                    .description(t.getDescription())
                    .occurredAt(t.getCreatedAt())
                    .build());
            if (t.getCompletedAt() != null) {
                events.add(ActivityEvent.builder()
                        .id(t.getId())
                        .type("TASK_COMPLETED")
                        .title("Task completed: " + t.getTitle())
                        .occurredAt(t.getCompletedAt())
                        .build());
            }
        }

        // Sort newest first
        events.sort(Comparator.comparing(
                (ActivityEvent a) -> a.getOccurredAt() != null ? a.getOccurredAt() : LocalDateTime.MIN
        ).reversed());

        return events;
    }
}
