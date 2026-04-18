package com.leadrush.enrichment.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-workspace configuration for an enrichment provider.
 * API keys are AES-encrypted (same EncryptionService used for mailbox passwords).
 */
@Entity
@Table(name = "enrichment_providers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "provider_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"apiKeyEncrypted"})
public class EnrichmentProvider extends TenantEntity {

    /** Provider identifier — matches the key in EnrichmentProviderAdapter.providerKey() */
    @Column(name = "provider_key", nullable = false)
    private String providerKey;

    /** Encrypted API key. Null for providers that don't need auth (e.g., MOCK). */
    @Column(name = "api_key_encrypted", columnDefinition = "text")
    private String apiKeyEncrypted;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Lower = tried first in the waterfall. */
    @Column(nullable = false)
    @Builder.Default
    private int priority = 100;

    // ── Usage tracking ──

    @Column(name = "calls_this_month")
    @Builder.Default
    private int callsThisMonth = 0;

    @Column(name = "calls_month_date")
    private LocalDate callsMonthDate;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    /** Increment the month call counter. Resets when a new month starts. */
    public void recordUse() {
        LocalDate now = LocalDate.now();
        if (callsMonthDate == null
                || callsMonthDate.getMonthValue() != now.getMonthValue()
                || callsMonthDate.getYear() != now.getYear()) {
            callsMonthDate = now;
            callsThisMonth = 0;
        }
        callsThisMonth++;
        lastUsedAt = LocalDateTime.now();
    }
}
