package com.leadrush.workspace.entity;

/**
 * Roles within a workspace (NOT the same as SystemRole which is platform-level).
 *
 * Each user has a role PER workspace:
 *   - User A might be OWNER of Workspace 1 and MEMBER of Workspace 2
 *   - User B might be ADMIN of Workspace 1 and VIEWER of Workspace 3
 */
public enum WorkspaceRole {
    OWNER,      // Full control — can delete workspace, transfer ownership
    ADMIN,      // Manage members, billing, settings
    MANAGER,    // Manage team work, view reports
    MEMBER,     // Standard access — CRUD on contacts, deals, etc.
    VIEWER      // Read-only access
}
