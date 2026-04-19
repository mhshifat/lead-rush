package com.leadrush.enrichment.repository;

import com.leadrush.enrichment.entity.DomainEmailPattern;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DomainEmailPatternRepository extends JpaRepository<DomainEmailPattern, UUID> {

    Optional<DomainEmailPattern> findByWorkspaceIdAndDomain(UUID workspaceId, String domain);
}
