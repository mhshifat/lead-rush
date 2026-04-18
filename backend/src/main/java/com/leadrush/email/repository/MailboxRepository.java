package com.leadrush.email.repository;

import com.leadrush.email.entity.Mailbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MailboxRepository extends JpaRepository<Mailbox, UUID> {

    List<Mailbox> findByWorkspaceId(UUID workspaceId);

    Optional<Mailbox> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
