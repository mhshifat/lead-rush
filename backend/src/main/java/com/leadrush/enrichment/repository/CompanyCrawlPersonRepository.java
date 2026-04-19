package com.leadrush.enrichment.repository;

import com.leadrush.enrichment.entity.CompanyCrawlPerson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyCrawlPersonRepository extends JpaRepository<CompanyCrawlPerson, UUID> {

    List<CompanyCrawlPerson> findByWorkspaceIdAndDomain(UUID workspaceId, String domain);

    void deleteByWorkspaceIdAndDomain(UUID workspaceId, String domain);
}
