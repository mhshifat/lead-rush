package com.leadrush.pipeline.entity;

import com.leadrush.common.TenantEntity;
import com.leadrush.contact.entity.Contact;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Deal — an opportunity that moves through pipeline stages.
 */
@Entity
@Table(name = "deals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"contacts"})
public class Deal extends TenantEntity {

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_stage_id", nullable = false)
    private PipelineStage stage;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "value_amount", precision = 14, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "value_currency", length = 3)
    @Builder.Default
    private String valueCurrency = "USD";

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "expected_close_at")
    private LocalDate expectedCloseAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // ── Contacts (many-to-many) ──

    @ManyToMany
    @JoinTable(
        name = "deal_contacts",
        joinColumns = @JoinColumn(name = "deal_id"),
        inverseJoinColumns = @JoinColumn(name = "contact_id")
    )
    @Builder.Default
    private Set<Contact> contacts = new HashSet<>();
}
