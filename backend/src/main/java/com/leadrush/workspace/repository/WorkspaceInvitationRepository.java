package com.leadrush.workspace.repository;

import com.leadrush.workspace.entity.WorkspaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    /** Token lookup — used on the public accept endpoint (no tenant context). */
    Optional<WorkspaceInvitation> findByToken(String token);

    /** All invitations sent from a workspace, newest first. */
    List<WorkspaceInvitation> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    /** Used to block duplicate pending invitations for the same email. */
    Optional<WorkspaceInvitation> findByWorkspaceIdAndEmailIgnoreCaseAndStatus(
            UUID workspaceId, String email, WorkspaceInvitation.Status status);

    Optional<WorkspaceInvitation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
