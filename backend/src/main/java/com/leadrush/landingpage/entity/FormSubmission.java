package com.leadrush.landingpage.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An individual form submission — captured data + attribution.
 */
@Entity
@Table(name = "form_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormSubmission extends TenantEntity {

    @Column(name = "form_id", nullable = false)
    private UUID formId;

    @Column(name = "landing_page_id")
    private UUID landingPageId;

    /** Contact created or matched by this submission. */
    @Column(name = "contact_id")
    private UUID contactId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String data;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(length = 500)
    private String referrer;

    @Column(name = "utm_source", length = 100)
    private String utmSource;

    @Column(name = "utm_medium", length = 100)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 100)
    private String utmCampaign;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();
}
