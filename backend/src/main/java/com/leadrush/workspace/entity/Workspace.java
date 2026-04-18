package com.leadrush.workspace.entity;

import com.leadrush.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Workspace entity — maps to the "workspaces" table.
 *
 * This IS the tenant. Every business entity (Contact, Deal, Sequence, etc.)
 * belongs to a workspace via workspace_id.
 *
 * GLOBAL entity (extends BaseEntity, NOT TenantEntity) because
 * the workspace IS the tenant — it doesn't belong to another workspace.
 *
 * The 'settings' field is JSONB — stored as a JSON string in PostgreSQL.
 * This is flexible: we can add new settings without a migration.
 * Example: {"timezone": "UTC", "defaultCurrency": "USD", "features": {...}}
 */
@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String logoUrl;

    /**
     * Flexible settings stored as JSON.
     *
     * @Column(columnDefinition = "jsonb") tells Hibernate to use PostgreSQL's JSONB type.
     * In Java we store it as a String and parse it when needed.
     * (We could also use a Map<String, Object> with a JPA converter, but String is simpler for now.)
     */
    /**
     * @JdbcTypeCode(SqlTypes.JSON) tells Hibernate:
     *   "When sending this String to PostgreSQL, cast it as JSON, not VARCHAR."
     *   Without this, PostgreSQL rejects: "column is jsonb but expression is varchar"
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String settings = "{}";
}
