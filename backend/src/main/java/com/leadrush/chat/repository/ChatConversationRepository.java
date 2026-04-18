package com.leadrush.chat.repository;

import com.leadrush.chat.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    /** Visitor lookup — called on every incoming widget message. */
    Optional<ChatConversation> findByVisitorToken(String visitorToken);

    Optional<ChatConversation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Inbox list — newest-activity first. */
    Page<ChatConversation> findByWorkspaceIdOrderByLastMessageAtDesc(UUID workspaceId, Pageable pageable);

    /** Total unread messages across the workspace — powers the sidebar badge. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(c.unreadByTeam), 0)
        FROM ChatConversation c
        WHERE c.workspaceId = :workspaceId AND c.status = com.leadrush.chat.entity.ChatConversation.Status.OPEN
    """)
    long sumUnreadByTeam(@org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId);
}
