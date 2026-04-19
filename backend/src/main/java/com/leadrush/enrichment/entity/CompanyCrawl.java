package com.leadrush.enrichment.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Queue + cache row for background domain crawls. One per (workspace, domain).
// Status starts PENDING, flips to IN_PROGRESS while a worker is running it, then
// COMPLETED or FAILED. next_attempt_at drives retry scheduling.
@Entity
@Table(name = "company_crawls",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "domain"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCrawl extends TenantEntity {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    @Column(nullable = false)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    @Column(name = "persons_found", nullable = false)
    @Builder.Default
    private int personsFound = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_crawled_at")
    private LocalDateTime lastCrawledAt;
}
