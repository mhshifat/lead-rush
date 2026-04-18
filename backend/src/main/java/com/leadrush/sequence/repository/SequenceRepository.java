package com.leadrush.sequence.repository;

import com.leadrush.sequence.entity.Sequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SequenceRepository extends JpaRepository<Sequence, UUID> {

    List<Sequence> findByWorkspaceId(UUID workspaceId);

    Optional<Sequence> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndStatus(UUID workspaceId, com.leadrush.sequence.entity.SequenceStatus status);
}
