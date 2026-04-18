package com.leadrush.email.tracking;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Cached SPF/DKIM/DMARC DNS lookup results for a sending domain.
 *
 * Why cache? DNS lookups take ~100-500ms each. We check SPF, DKIM, DMARC
 * for every domain = 3 DNS lookups. Caching for 24 hours is reasonable —
 * DNS records change rarely.
 *
 * Status values: PASS (record found & valid), FAIL (record found but wrong),
 * NOT_FOUND (no record), ERROR (DNS lookup failed).
 */
@Entity
@Table(name = "email_deliverability_checks",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "domain"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailDeliverabilityCheck extends TenantEntity {

    @Column(nullable = false)
    private String domain;

    // ── SPF ──
    @Column(name = "spf_status")
    private String spfStatus;

    @Column(name = "spf_record", columnDefinition = "text")
    private String spfRecord;

    // ── DKIM ──
    @Column(name = "dkim_status")
    private String dkimStatus;

    @Column(name = "dkim_selector")
    private String dkimSelector;

    @Column(name = "dkim_record", columnDefinition = "text")
    private String dkimRecord;

    // ── DMARC ──
    @Column(name = "dmarc_status")
    private String dmarcStatus;

    @Column(name = "dmarc_record", columnDefinition = "text")
    private String dmarcRecord;

    @Column(name = "checked_at", nullable = false)
    @Builder.Default
    private LocalDateTime checkedAt = LocalDateTime.now();
}
