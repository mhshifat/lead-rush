package com.leadrush.tag.entity;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Tag entity — flexible labels for contacts.
 *
 * Tags are per-workspace and have optional colors.
 * A contact can have many tags (ManyToMany — defined on Contact entity).
 */
@Entity
@Table(name = "tags",
       uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag extends TenantEntity {

    @Column(nullable = false)
    private String name;

    private String color;       // hex: "#3B82F6"
}
