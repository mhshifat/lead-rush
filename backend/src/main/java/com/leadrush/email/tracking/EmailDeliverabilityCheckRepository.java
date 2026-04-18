package com.leadrush.email.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailDeliverabilityCheckRepository extends JpaRepository<EmailDeliverabilityCheck, UUID> {

    Optional<EmailDeliverabilityCheck> findByWorkspaceIdAndDomain(UUID workspaceId, String domain);
}
