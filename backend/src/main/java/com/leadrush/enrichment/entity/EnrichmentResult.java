package com.leadrush.enrichment.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cached enrichment result from one provider for one contact.
 *
 * Cache behavior: if a SUCCESS result exists within 90 days, we skip re-calling the API.
 * NOT_FOUND results are also cached (so we don't repeatedly call a provider that
 * can't find this contact).
 */
@Entity
@Table(name = "enrichment_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichmentResult extends TenantEntity {

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "provider_key", nullable = false)
    private String providerKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    // Extracted fields
    @Column(name = "found_email")
    private String foundEmail;

    @Column(name = "found_phone", length = 50)
    private String foundPhone;

    @Column(name = "found_title")
    private String foundTitle;

    @Column(name = "found_linkedin_url", length = 500)
    private String foundLinkedinUrl;

    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "enriched_at", nullable = false)
    @Builder.Default
    private LocalDateTime enrichedAt = LocalDateTime.now();

    public enum ResultStatus {
        SUCCESS,        // Provider returned data
        NOT_FOUND,      // Provider responded but didn't find the contact
        ERROR,          // API call failed (network, auth, etc.)
        RATE_LIMITED    // Provider rate limit hit
    }
}
