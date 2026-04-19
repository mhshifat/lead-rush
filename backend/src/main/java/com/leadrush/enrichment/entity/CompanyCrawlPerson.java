package com.leadrush.enrichment.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// A person record extracted from a crawled company page. Many per CompanyCrawl.
// Queried by (workspaceId, domain) and optionally filtered by name when an enrichment
// request comes in for a contact at that domain.
@Entity
@Table(name = "company_crawl_persons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyCrawlPerson extends TenantEntity {

    @Column(nullable = false)
    private String domain;

    private String name;
    private String email;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "source_adapter", length = 50)
    private String sourceAdapter;

    @Column(name = "discovered_at", nullable = false)
    @Builder.Default
    private LocalDateTime discoveredAt = LocalDateTime.now();
}
