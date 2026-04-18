package com.leadrush.workspace.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.workspace.dto.*;
import com.leadrush.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Workspace management endpoints — all scoped to the current workspace from JWT
 * except `/workspaces/mine` which lists workspaces the caller belongs to.
 */
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /** All workspaces the caller belongs to — powers the workspace switcher. */
    @GetMapping("/mine")
    public ApiResponse<List<WorkspaceResponse>> mine() {
        return ApiResponse.success(workspaceService.listMyWorkspaces());
    }

    /** The workspace currently in the JWT. */
    @GetMapping("/current")
    public ApiResponse<WorkspaceResponse> current() {
        return ApiResponse.success(workspaceService.getCurrentWorkspace());
    }

    @PostMapping
    public ApiResponse<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.createWorkspace(request));
    }

    /** Update name / logo of the CURRENT workspace. OWNER or ADMIN only. */
    @PutMapping("/current")
    public ApiResponse<WorkspaceResponse> update(@Valid @RequestBody UpdateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.updateWorkspace(request));
    }

    /** Current caller leaves the current workspace. */
    @DeleteMapping("/current/leave")
    public ApiResponse<Void> leave() {
        workspaceService.leaveWorkspace();
        return ApiResponse.success(null);
    }

    // ── Members ──

    @GetMapping("/current/members")
    public ApiResponse<List<MemberResponse>> members() {
        return ApiResponse.success(workspaceService.listMembers());
    }

    @PutMapping("/current/members/{membershipId}")
    public ApiResponse<MemberResponse> updateMemberRole(
            @PathVariable UUID membershipId,
            @Valid @RequestBody UpdateMemberRoleRequest request
    ) {
        return ApiResponse.success(workspaceService.updateMemberRole(membershipId, request));
    }

    @DeleteMapping("/current/members/{membershipId}")
    public ApiResponse<Void> removeMember(@PathVariable UUID membershipId) {
        workspaceService.removeMember(membershipId);
        return ApiResponse.success(null);
    }

    // ── Invitations ──

    @GetMapping("/current/invitations")
    public ApiResponse<List<InvitationResponse>> listInvitations() {
        return ApiResponse.success(workspaceService.listInvitations());
    }

    @PostMapping("/current/invitations")
    public ApiResponse<InvitationResponse> invite(@Valid @RequestBody InviteRequest request) {
        return ApiResponse.success(workspaceService.invite(request));
    }

    @DeleteMapping("/current/invitations/{id}")
    public ApiResponse<Void> revokeInvitation(@PathVariable UUID id) {
        workspaceService.revokeInvitation(id);
        return ApiResponse.success(null);
    }
}
