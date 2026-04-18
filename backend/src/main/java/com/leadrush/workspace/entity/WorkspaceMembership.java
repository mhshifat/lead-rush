package com.leadrush.workspace.entity;

import com.leadrush.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Workspace Membership — links a User to a Workspace with a role.
 *
 * This is the many-to-many junction table:
 *   User ←→ WorkspaceMembership ←→ Workspace
 *
 * A user can be a member of multiple workspaces (each with a different role).
 * A workspace can have multiple members.
 *
 * ROLES:
 *   OWNER   — full control, can delete workspace, transfer ownership
 *   ADMIN   — manage members, billing, settings
 *   MANAGER — manage team members' work, view reports
 *   MEMBER  — standard access (CRUD on contacts, deals, etc.)
 *   VIEWER  — read-only access
 */
@Entity
@Table(name = "workspace_memberships",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "workspace_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceMembership extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WorkspaceRole role = WorkspaceRole.MEMBER;
}
