package com.leadrush.email.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UnsubscribeRepository extends JpaRepository<Unsubscribe, UUID> {

    Optional<Unsubscribe> findByWorkspaceIdAndContactId(UUID workspaceId, UUID contactId);

    boolean existsByWorkspaceIdAndContactId(UUID workspaceId, UUID contactId);
}
