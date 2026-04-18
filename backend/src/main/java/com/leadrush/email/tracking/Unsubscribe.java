package com.leadrush.email.tracking;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Records a contact opting out of future emails.
 *
 * The SequenceExecutionJob checks this table before sending each email
 * and marks the enrollment as UNSUBSCRIBED if a record exists for the contact.
 *
 * Sources:
 *   LINK_CLICK — clicked the unsubscribe link in an email
 *   LIST_UNSUBSCRIBE — clicked Gmail/Outlook's native "Unsubscribe" button (RFC 8058)
 *   MANUAL — user added manually in the UI
 *   BOUNCE — hard bounce — email is invalid
 */
@Entity
@Table(name = "unsubscribes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "contact_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unsubscribe extends TenantEntity {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Source source = Source.LINK_CLICK;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "step_execution_id")
    private UUID stepExecutionId;

    public enum Source {
        LINK_CLICK,
        LIST_UNSUBSCRIBE,
        MANUAL,
        BOUNCE
    }
}
