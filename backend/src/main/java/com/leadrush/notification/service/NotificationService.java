package com.leadrush.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.notification.dto.NotificationResponse;
import com.leadrush.notification.entity.Notification;
import com.leadrush.notification.entity.NotificationType;
import com.leadrush.notification.repository.NotificationRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.workspace.entity.WorkspaceMembership;
import com.leadrush.workspace.repository.WorkspaceMembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates, lists, and delivers in-app notifications.
 *
 * Delivery: persist to DB + push via WebSocket to `/user/{userId}/queue/notifications`.
 * Spring's user destination router maps that to the authenticated session (see WebSocketConfig).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // ── Public API: create + push ──

    /**
     * Create + broadcast a notification to a specific user within a workspace.
     * Used by other services when something notable happens.
     *
     * Never throws — delivery failures are logged but don't break the triggering flow.
     */
    @Transactional
    public void notify(
            UUID workspaceId,
            UUID userId,
            NotificationType type,
            String title,
            String body,
            String linkPath,
            Map<String, Object> metadata
    ) {
        try {
            Notification notification = Notification.builder()
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .body(body)
                    .linkPath(linkPath)
                    .metadata(metadata != null ? objectMapper.writeValueAsString(metadata) : "{}")
                    .build();
            notification.setWorkspaceId(workspaceId);

            notification = notificationRepository.save(notification);

            // Push via WebSocket. If the user isn't connected, it just goes into the DB
            // and they'll see it on next load.
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    toResponse(notification)
            );

            log.debug("Notification sent: type={} user={} title='{}'", type, userId, title);
        } catch (Exception e) {
            log.warn("Failed to deliver notification (type={}, user={}): {}", type, userId, e.getMessage());
        }
    }

    /** Convenience: notify without metadata or link. */
    public void notify(UUID workspaceId, UUID userId, NotificationType type, String title, String body) {
        notify(workspaceId, userId, type, title, body, null, null);
    }

    /**
     * Broadcast a notification to every member of a workspace. Useful for events that
     * aren't owned by a single user (form submissions, sequence-generated tasks).
     */
    public void notifyWorkspace(UUID workspaceId, NotificationType type, String title, String body,
                                 String linkPath, Map<String, Object> metadata) {
        List<WorkspaceMembership> members = membershipRepository.findByWorkspaceId(workspaceId);
        for (WorkspaceMembership m : members) {
            notify(workspaceId, m.getUserId(), type, title, body, linkPath, metadata);
        }
    }

    // ── REST handlers ──

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(int page, int size) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        return notificationRepository
                .findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(wsId, userId, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listUnread() {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        return notificationRepository
                .findByWorkspaceIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(wsId, userId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        return notificationRepository.countByWorkspaceIdAndUserIdAndReadAtIsNull(wsId, userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        Notification notification = notificationRepository
                .findByIdAndWorkspaceIdAndUserId(id, wsId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead() {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        List<Notification> unread = notificationRepository
                .findByWorkspaceIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(wsId, userId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(n -> n.setReadAt(now));
        notificationRepository.saveAll(unread);
    }

    // ── Helpers ──

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .body(n.getBody())
                .linkPath(n.getLinkPath())
                .metadata(n.getMetadata())
                .readAt(n.getReadAt())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
