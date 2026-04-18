package com.leadrush.sequence.entity;

import com.leadrush.common.TenantEntity;
import com.leadrush.contact.entity.Contact;
import com.leadrush.email.entity.Mailbox;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * SequenceEnrollment — a contact's participation in a sequence.
 *
 * The scheduler (SequenceExecutionJob) finds enrollments where:
 *   status = ACTIVE AND next_execution_at <= NOW()
 * And executes the next step for each.
 */
@Entity
@Table(name = "sequence_enrollments",
       uniqueConstraints = @UniqueConstraint(columnNames = {"sequence_id", "contact_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"sequence", "contact", "mailbox"})
public class SequenceEnrollment extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sequence_id", nullable = false)
    private Sequence sequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mailbox_id")
    private Mailbox mailbox;

    @Column(name = "current_step_index")
    @Builder.Default
    private int currentStepIndex = 0;

    @Column(name = "next_execution_at")
    private LocalDateTime nextExecutionAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "enrolled_at", nullable = false)
    @Builder.Default
    private LocalDateTime enrolledAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
