package com.leadrush.chat.repository;

import com.leadrush.chat.entity.ChatWidget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatWidgetRepository extends JpaRepository<ChatWidget, UUID> {

    Optional<ChatWidget> findByWorkspaceId(UUID workspaceId);
}
