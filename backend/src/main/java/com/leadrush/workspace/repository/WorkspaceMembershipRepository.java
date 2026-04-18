package com.leadrush.workspace.repository;

import com.leadrush.workspace.entity.WorkspaceMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    /** Get all workspaces a user belongs to */
    List<WorkspaceMembership> findByUserId(UUID userId);

    /** Get a specific membership (user + workspace) */
    Optional<WorkspaceMembership> findByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    boolean existsByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);

    /** All members of a workspace — used for broadcasting workspace-wide notifications. */
    List<WorkspaceMembership> findByWorkspaceId(UUID workspaceId);
}
