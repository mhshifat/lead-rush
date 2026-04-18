package com.leadrush.notification.repository;

import com.leadrush.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByWorkspaceIdAndUserIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID userId, Pageable pageable);

    List<Notification> findByWorkspaceIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
            UUID workspaceId, UUID userId);

    long countByWorkspaceIdAndUserIdAndReadAtIsNull(UUID workspaceId, UUID userId);

    Optional<Notification> findByIdAndWorkspaceIdAndUserId(UUID id, UUID workspaceId, UUID userId);
}
