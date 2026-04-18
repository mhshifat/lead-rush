package com.leadrush.sequence.entity;

import com.leadrush.common.TenantEntity;
import com.leadrush.email.entity.Mailbox;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequence entity — a multi-step outreach campaign.
 *
 * Lifecycle:
 *   1. User creates sequence (status = DRAFT)
 *   2. User adds steps (EMAIL, DELAY, etc.)
 *   3. User activates sequence (status = ACTIVE)
 *   4. User enrolls contacts → scheduler executes steps over time
 *   5. Sequence completes, pauses, or stops based on replies
 */
@Entity
@Table(name = "sequences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"steps", "defaultMailbox"})
public class Sequence extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SequenceStatus status = SequenceStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_mailbox_id")
    private Mailbox defaultMailbox;

    @Column(name = "total_enrolled")
    @Builder.Default
    private int totalEnrolled = 0;

    @Column(name = "total_completed")
    @Builder.Default
    private int totalCompleted = 0;

    @Column(name = "total_replied")
    @Builder.Default
    private int totalReplied = 0;

    @OneToMany(mappedBy = "sequence", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<SequenceStep> steps = new ArrayList<>();

    public void addStep(SequenceStep step) {
        steps.add(step);
        step.setSequence(this);
    }
}
