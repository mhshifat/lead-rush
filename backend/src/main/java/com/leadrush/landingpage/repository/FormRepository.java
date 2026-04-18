package com.leadrush.landingpage.repository;

import com.leadrush.landingpage.entity.Form;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository extends JpaRepository<Form, UUID> {

    List<Form> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<Form> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
