package com.leadrush.sequence.job;

import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Picks up sequence enrollments due for execution; relies on the
// idx_enrollments_due partial index for cheap polling.
@Component
@RequiredArgsConstructor
@Slf4j
public class SequenceExecutionJob {

    private final SequenceEnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void processDueEnrollments() {
        List<SequenceEnrollment> due = enrollmentRepository.findDueEnrollments(LocalDateTime.now());

        if (due.isEmpty()) {
            return;
        }

        log.info("Processing {} due enrollments", due.size());

        for (SequenceEnrollment enrollment : due) {
            try {
                // Each executeNextStep is its own transaction so one failure
                // doesn't roll back other enrollments.
                enrollmentService.executeNextStep(enrollment.getId());
            } catch (Exception e) {
                log.error("Failed to process enrollment {}: {}", enrollment.getId(), e.getMessage(), e);
            }
        }
    }
}
