package com.leadrush.leadscoring.repository;

import com.leadrush.leadscoring.entity.LeadScoreLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeadScoreLogRepository extends JpaRepository<LeadScoreLog, UUID> {

    /** Score change history for a single contact — shown on contact detail page. */
    List<LeadScoreLog> findByWorkspaceIdAndContactIdOrderByCreatedAtDesc(UUID workspaceId, UUID contactId);
}
