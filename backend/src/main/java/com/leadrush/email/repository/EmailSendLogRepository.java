package com.leadrush.email.repository;

import com.leadrush.email.entity.EmailSendLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, UUID> {

    List<EmailSendLog> findByWorkspaceIdAndContactIdOrderByCreatedAtDesc(UUID workspaceId, UUID contactId);

    /**
     * Per-mailbox aggregate since a cutoff: [mailboxId, sent, failed, bounced].
     * Powers the mailbox health report.
     */
    @Query("""
        SELECT l.mailboxId,
               SUM(CASE WHEN l.status = com.leadrush.email.entity.EmailSendLog.SendStatus.SENT THEN 1 ELSE 0 END),
               SUM(CASE WHEN l.status = com.leadrush.email.entity.EmailSendLog.SendStatus.FAILED THEN 1 ELSE 0 END),
               SUM(CASE WHEN l.status = com.leadrush.email.entity.EmailSendLog.SendStatus.BOUNCED THEN 1 ELSE 0 END)
        FROM EmailSendLog l
        WHERE l.workspaceId = :workspaceId AND l.createdAt >= :since
        GROUP BY l.mailboxId
    """)
    List<Object[]> aggregateByMailbox(@Param("workspaceId") UUID workspaceId,
                                       @Param("since") LocalDateTime since);
}
