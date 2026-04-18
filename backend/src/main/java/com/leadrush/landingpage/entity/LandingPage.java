package com.leadrush.landingpage.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Block-based landing page — like Leadpages or Unbounce.
 *
 * The "blocks" column is a JSON array of block objects:
 *   [{ "id": "uuid", "type": "hero", "props": { ... } }, ...]
 *
 * Block types supported: hero, text, image, form, cta, columns
 * Each block type has its own props schema (handled by the renderer and editor).
 *
 * Slugs are unique per workspace — the public URL is /p/{workspace-slug}/{page-slug}
 * but for now we just use /p/{page-slug} and rely on unique global slugs.
 */
@Entity
@Table(name = "landing_pages",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "slug"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LandingPage extends TenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(name = "meta_title")
    private String metaTitle;

    @Column(name = "meta_description", length = 500)
    private String metaDescription;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private String blocks = "[]";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "conversion_count", nullable = false)
    @Builder.Default
    private int conversionCount = 0;

    public enum Status { DRAFT, PUBLISHED }
}
