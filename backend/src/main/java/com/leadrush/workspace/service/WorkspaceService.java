package com.leadrush.workspace.service;

import com.leadrush.auth.entity.User;
import com.leadrush.auth.repository.UserRepository;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ConflictException;
import com.leadrush.common.exception.ForbiddenException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.email.service.TransactionalEmailService;
import com.leadrush.security.TenantContext;
import com.leadrush.workspace.dto.*;
import com.leadrush.workspace.entity.Workspace;
import com.leadrush.workspace.entity.WorkspaceInvitation;
import com.leadrush.workspace.entity.WorkspaceMembership;
import com.leadrush.workspace.entity.WorkspaceRole;
import com.leadrush.workspace.repository.WorkspaceInvitationRepository;
import com.leadrush.workspace.repository.WorkspaceMembershipRepository;
import com.leadrush.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Workspace CRUD + member management + invitations.
 *
 * Authorization rules (enforced here, not via @PreAuthorize since roles aren't Spring authorities):
 *   - Anyone in the workspace → list members, list invitations, view details
 *   - OWNER/ADMIN → invite, revoke, remove, change roles
 *   - OWNER       → can modify OWNER/ADMIN roles, can delete workspace
 *   - You can't remove the last OWNER (would orphan the workspace)
 *   - You can't demote yourself if you're the last OWNER
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final WorkspaceInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final TransactionalEmailService transactionalEmailService;

    /** Roles allowed to manage the workspace (invite, remove, change roles). */
    private static final Set<WorkspaceRole> MANAGER_ROLES = Set.of(WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

    // ── Workspaces ──

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> listMyWorkspaces() {
        UUID userId = TenantContext.getUserId();
        List<WorkspaceMembership> memberships = membershipRepository.findByUserId(userId);
        return memberships.stream().map(m -> {
            Workspace ws = workspaceRepository.findById(m.getWorkspaceId()).orElse(null);
            if (ws == null) return null;
            return WorkspaceResponse.builder()
                    .id(ws.getId())
                    .name(ws.getName())
                    .slug(ws.getSlug())
                    .logoUrl(ws.getLogoUrl())
                    .role(m.getRole().name())
                    .memberCount(membershipRepository.findByWorkspaceId(ws.getId()).size())
                    .createdAt(ws.getCreatedAt())
                    .build();
        }).filter(w -> w != null).toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse getCurrentWorkspace() {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        Workspace ws = workspaceRepository.findById(wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", wsId));
        WorkspaceMembership m = membershipRepository.findByUserIdAndWorkspaceId(userId, wsId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this workspace"));
        return WorkspaceResponse.builder()
                .id(ws.getId())
                .name(ws.getName())
                .slug(ws.getSlug())
                .logoUrl(ws.getLogoUrl())
                .role(m.getRole().name())
                .memberCount(membershipRepository.findByWorkspaceId(wsId).size())
                .createdAt(ws.getCreatedAt())
                .build();
    }

    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request) {
        UUID userId = TenantContext.getUserId();

        Workspace ws = Workspace.builder()
                .name(request.getName())
                .slug(generateSlug(request.getName()))
                .build();
        ws = workspaceRepository.save(ws);

        WorkspaceMembership membership = WorkspaceMembership.builder()
                .userId(userId)
                .workspaceId(ws.getId())
                .role(WorkspaceRole.OWNER)
                .build();
        membershipRepository.save(membership);

        log.info("Workspace created: {} by user {}", ws.getName(), userId);
        return WorkspaceResponse.builder()
                .id(ws.getId()).name(ws.getName()).slug(ws.getSlug()).logoUrl(ws.getLogoUrl())
                .role(WorkspaceRole.OWNER.name()).memberCount(1).createdAt(ws.getCreatedAt())
                .build();
    }

    @Transactional
    public WorkspaceResponse updateWorkspace(UpdateWorkspaceRequest request) {
        requireRole(MANAGER_ROLES);
        UUID wsId = TenantContext.getWorkspaceId();
        Workspace ws = workspaceRepository.findById(wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", wsId));

        if (request.getName() != null && !request.getName().isBlank()) ws.setName(request.getName());
        if (request.getLogoUrl() != null) ws.setLogoUrl(request.getLogoUrl());
        ws = workspaceRepository.save(ws);

        return getCurrentWorkspace();
    }

    // ── Members ──

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        UUID wsId = TenantContext.getWorkspaceId();
        List<WorkspaceMembership> memberships = membershipRepository.findByWorkspaceId(wsId);

        return memberships.stream().map(m -> {
            User user = userRepository.findById(m.getUserId()).orElse(null);
            if (user == null) return null;
            return MemberResponse.builder()
                    .membershipId(m.getId())
                    .userId(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .avatarUrl(user.getAvatarUrl())
                    .role(m.getRole().name())
                    .joinedAt(m.getCreatedAt())
                    .build();
        }).filter(r -> r != null).toList();
    }

    @Transactional
    public MemberResponse updateMemberRole(UUID membershipId, UpdateMemberRoleRequest request) {
        requireRole(MANAGER_ROLES);
        UUID wsId = TenantContext.getWorkspaceId();

        WorkspaceMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));
        if (!target.getWorkspaceId().equals(wsId)) {
            throw new ResourceNotFoundException("Membership", membershipId);
        }

        WorkspaceRole newRole = parseRole(request.getRole());
        WorkspaceRole callerRole = getCallerRole();

        // Only OWNER can touch OWNER/ADMIN rows
        if ((target.getRole() == WorkspaceRole.OWNER || newRole == WorkspaceRole.OWNER
                || target.getRole() == WorkspaceRole.ADMIN)
                && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only owners can modify owner or admin roles");
        }

        // Can't demote the last OWNER
        if (target.getRole() == WorkspaceRole.OWNER && newRole != WorkspaceRole.OWNER
                && countOwners(wsId) <= 1) {
            throw new BusinessException("You can't demote the last owner of this workspace");
        }

        target.setRole(newRole);
        membershipRepository.save(target);

        User user = userRepository.findById(target.getUserId()).orElseThrow();
        return MemberResponse.builder()
                .membershipId(target.getId()).userId(user.getId())
                .name(user.getName()).email(user.getEmail()).avatarUrl(user.getAvatarUrl())
                .role(newRole.name()).joinedAt(target.getCreatedAt())
                .build();
    }

    @Transactional
    public void removeMember(UUID membershipId) {
        requireRole(MANAGER_ROLES);
        UUID wsId = TenantContext.getWorkspaceId();

        WorkspaceMembership target = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));
        if (!target.getWorkspaceId().equals(wsId)) {
            throw new ResourceNotFoundException("Membership", membershipId);
        }

        WorkspaceRole callerRole = getCallerRole();
        if (target.getRole() == WorkspaceRole.OWNER && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only owners can remove owners");
        }
        if (target.getRole() == WorkspaceRole.OWNER && countOwners(wsId) <= 1) {
            throw new BusinessException("You can't remove the last owner");
        }

        membershipRepository.delete(target);
    }

    /** A user leaves a workspace voluntarily. Same last-owner guard applies. */
    @Transactional
    public void leaveWorkspace() {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();

        WorkspaceMembership membership = membershipRepository.findByUserIdAndWorkspaceId(userId, wsId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this workspace"));

        if (membership.getRole() == WorkspaceRole.OWNER && countOwners(wsId) <= 1) {
            throw new BusinessException("You can't leave as the last owner. Transfer ownership first.");
        }
        membershipRepository.delete(membership);
    }

    // ── Invitations ──

    @Transactional
    public InvitationResponse invite(InviteRequest request) {
        requireRole(MANAGER_ROLES);
        UUID wsId = TenantContext.getWorkspaceId();
        UUID inviterId = TenantContext.getUserId();

        String email = request.getEmail().trim().toLowerCase();
        WorkspaceRole role = request.getRole() == null || request.getRole().isBlank()
                ? WorkspaceRole.MEMBER
                : parseRole(request.getRole());

        // Only OWNER can invite as OWNER/ADMIN
        WorkspaceRole callerRole = getCallerRole();
        if ((role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN)
                && callerRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only owners can invite owners or admins");
        }

        // Block if user is already a member
        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()
                && membershipRepository.existsByUserIdAndWorkspaceId(existingUser.get().getId(), wsId)) {
            throw new ConflictException("That user is already a member of this workspace");
        }

        // Block duplicate pending invite
        invitationRepository.findByWorkspaceIdAndEmailIgnoreCaseAndStatus(
                wsId, email, WorkspaceInvitation.Status.PENDING
        ).ifPresent(inv -> {
            throw new ConflictException("There is already a pending invitation for this email");
        });

        WorkspaceInvitation invitation = WorkspaceInvitation.builder()
                .workspaceId(wsId)
                .invitedByUserId(inviterId)
                .email(email)
                .role(role)
                .token(UUID.randomUUID().toString())
                .status(WorkspaceInvitation.Status.PENDING)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        invitation = invitationRepository.save(invitation);

        // Fire the email
        Workspace ws = workspaceRepository.findById(wsId).orElseThrow();
        User inviter = userRepository.findById(inviterId).orElseThrow();
        transactionalEmailService.sendInvitationEmail(
                email, ws.getName(), inviter.getName(), invitation.getToken());

        return toInvitationResponse(invitation, ws.getName(), inviter.getName());
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listInvitations() {
        UUID wsId = TenantContext.getWorkspaceId();
        return invitationRepository.findByWorkspaceIdOrderByCreatedAtDesc(wsId).stream()
                .map(inv -> {
                    Workspace ws = workspaceRepository.findById(inv.getWorkspaceId()).orElse(null);
                    User inviter = userRepository.findById(inv.getInvitedByUserId()).orElse(null);
                    return toInvitationResponse(inv,
                            ws != null ? ws.getName() : null,
                            inviter != null ? inviter.getName() : null);
                }).toList();
    }

    @Transactional
    public void revokeInvitation(UUID id) {
        requireRole(MANAGER_ROLES);
        UUID wsId = TenantContext.getWorkspaceId();
        WorkspaceInvitation inv = invitationRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", id));
        if (inv.getStatus() != WorkspaceInvitation.Status.PENDING) {
            throw new BusinessException("Only pending invitations can be revoked");
        }
        inv.setStatus(WorkspaceInvitation.Status.REVOKED);
        invitationRepository.save(inv);
    }

    /**
     * Public preview of an invitation — used on the accept page to show the
     * recipient which workspace they were invited to before they authenticate.
     */
    @Transactional(readOnly = true)
    public InvitationResponse previewInvitation(String token) {
        WorkspaceInvitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", token));
        Workspace ws = workspaceRepository.findById(inv.getWorkspaceId()).orElse(null);
        User inviter = userRepository.findById(inv.getInvitedByUserId()).orElse(null);
        return toInvitationResponse(inv,
                ws != null ? ws.getName() : null,
                inviter != null ? inviter.getName() : null);
    }

    /**
     * Accept an invitation. The caller must already be logged in and their
     * email must match the invitation's email.
     */
    @Transactional
    public WorkspaceResponse acceptInvitation(String token) {
        UUID userId = TenantContext.getUserId();
        if (userId == null) {
            throw new ForbiddenException("Sign in to accept this invitation");
        }

        WorkspaceInvitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", token));

        if (inv.getStatus() != WorkspaceInvitation.Status.PENDING) {
            throw new BusinessException("This invitation is no longer valid");
        }
        if (inv.isExpired()) {
            inv.setStatus(WorkspaceInvitation.Status.EXPIRED);
            invitationRepository.save(inv);
            throw new BusinessException("This invitation has expired");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!user.getEmail().equalsIgnoreCase(inv.getEmail())) {
            throw new ForbiddenException(
                    "This invitation was sent to a different email. Sign in with " + inv.getEmail());
        }

        // Already a member? Mark the invite accepted but don't create a duplicate membership.
        if (!membershipRepository.existsByUserIdAndWorkspaceId(user.getId(), inv.getWorkspaceId())) {
            WorkspaceMembership membership = WorkspaceMembership.builder()
                    .userId(user.getId())
                    .workspaceId(inv.getWorkspaceId())
                    .role(inv.getRole())
                    .build();
            membershipRepository.save(membership);
        }

        inv.setStatus(WorkspaceInvitation.Status.ACCEPTED);
        inv.setAcceptedAt(LocalDateTime.now());
        inv.setAcceptedByUserId(user.getId());
        invitationRepository.save(inv);

        // Return the workspace they just joined (not the one currently in JWT)
        Workspace ws = workspaceRepository.findById(inv.getWorkspaceId()).orElseThrow();
        return WorkspaceResponse.builder()
                .id(ws.getId()).name(ws.getName()).slug(ws.getSlug()).logoUrl(ws.getLogoUrl())
                .role(inv.getRole().name())
                .memberCount(membershipRepository.findByWorkspaceId(ws.getId()).size())
                .createdAt(ws.getCreatedAt())
                .build();
    }

    // ── Helpers ──

    private InvitationResponse toInvitationResponse(WorkspaceInvitation inv, String wsName, String inviterName) {
        return InvitationResponse.builder()
                .id(inv.getId())
                .email(inv.getEmail())
                .role(inv.getRole().name())
                .status(inv.getStatus().name())
                .invitedByName(inviterName)
                .workspaceName(wsName)
                .expiresAt(inv.getExpiresAt())
                .acceptedAt(inv.getAcceptedAt())
                .createdAt(inv.getCreatedAt())
                .build();
    }

    private WorkspaceRole getCallerRole() {
        UUID userId = TenantContext.getUserId();
        UUID wsId = TenantContext.getWorkspaceId();
        return membershipRepository.findByUserIdAndWorkspaceId(userId, wsId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this workspace"))
                .getRole();
    }

    private void requireRole(Set<WorkspaceRole> allowed) {
        if (!allowed.contains(getCallerRole())) {
            throw new ForbiddenException("You don't have permission to do that");
        }
    }

    private int countOwners(UUID wsId) {
        return (int) membershipRepository.findByWorkspaceId(wsId).stream()
                .filter(m -> m.getRole() == WorkspaceRole.OWNER)
                .count();
    }

    private WorkspaceRole parseRole(String role) {
        try {
            return WorkspaceRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid role: " + role);
        }
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "workspace";
        if (workspaceRepository.existsBySlug(base)) {
            return base + "-" + UUID.randomUUID().toString().substring(0, 4);
        }
        return base;
    }
}
