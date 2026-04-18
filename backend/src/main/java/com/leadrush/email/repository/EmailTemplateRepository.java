package com.leadrush.email.repository;

import com.leadrush.email.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {

    List<EmailTemplate> findByWorkspaceId(UUID workspaceId);

    Optional<EmailTemplate> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
