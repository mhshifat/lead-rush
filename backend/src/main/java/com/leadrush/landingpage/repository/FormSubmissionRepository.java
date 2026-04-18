package com.leadrush.landingpage.repository;

import com.leadrush.landingpage.entity.FormSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

    Page<FormSubmission> findByWorkspaceIdOrderBySubmittedAtDesc(UUID workspaceId, Pageable pageable);

    Page<FormSubmission> findByFormIdOrderBySubmittedAtDesc(UUID formId, Pageable pageable);
}
