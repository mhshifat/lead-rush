package com.leadrush.sequence.repository;

import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.sequence.entity.SequenceEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SequenceEnrollmentRepository extends JpaRepository<SequenceEnrollment, UUID> {

    List<SequenceEnrollment> findByWorkspaceId(UUID workspaceId);

    List<SequenceEnrollment> findBySequenceId(UUID sequenceId);

    List<SequenceEnrollment> findByContactId(UUID contactId);

    Optional<SequenceEnrollment> findBySequenceIdAndContactId(UUID sequenceId, UUID contactId);

    /**
     * Find all enrollments whose next step is due.
     * Called by SequenceExecutionJob every minute.
     *
     * WHY @Query with JPQL?
     *   Derived queries can't handle "IN (list of enum values)" well.
     *   JPQL makes it explicit and readable.
     */
    @Query("""
        SELECT e FROM SequenceEnrollment e
        WHERE e.status = 'ACTIVE'
        AND e.nextExecutionAt <= :now
        ORDER BY e.nextExecutionAt ASC
    """)
    List<SequenceEnrollment> findDueEnrollments(@Param("now") LocalDateTime now);

    long countBySequenceIdAndStatus(UUID sequenceId, EnrollmentStatus status);

    long countByWorkspaceId(UUID workspaceId);
}
