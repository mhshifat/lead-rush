package com.leadrush.tag.repository;

import com.leadrush.tag.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByWorkspaceId(UUID workspaceId);

    Optional<Tag> findByWorkspaceIdAndName(UUID workspaceId, String name);
}
