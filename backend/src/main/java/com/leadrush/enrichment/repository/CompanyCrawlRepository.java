package com.leadrush.enrichment.repository;

import com.leadrush.enrichment.entity.CompanyCrawl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyCrawlRepository extends JpaRepository<CompanyCrawl, UUID> {

    Optional<CompanyCrawl> findByWorkspaceIdAndDomain(UUID workspaceId, String domain);

    // Due crawls — PENDING, or FAILED but still under the max-attempt ceiling and past the backoff.
    // Ordered oldest first so starving domains eventually get tried.
    @Query("""
           SELECT c FROM CompanyCrawl c
           WHERE c.status IN (com.leadrush.enrichment.entity.CompanyCrawl.Status.PENDING,
                              com.leadrush.enrichment.entity.CompanyCrawl.Status.FAILED)
             AND c.nextAttemptAt <= :now
             AND c.attemptCount < 3
           ORDER BY c.nextAttemptAt ASC
           """)
    List<CompanyCrawl> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    // Company domains that have contacts but no crawl row yet. Native query so the
    // tenant @Filter doesn't interfere — the sweep runs outside any workspace context.
    interface WorkspaceDomain {
        UUID getWorkspaceId();
        String getDomain();
    }

    @Query(value = """
           SELECT DISTINCT c.workspace_id AS workspaceId, LOWER(c.domain) AS domain
           FROM companies c
           WHERE c.domain IS NOT NULL AND c.domain <> ''
             AND NOT EXISTS (
               SELECT 1 FROM company_crawls cc
               WHERE cc.workspace_id = c.workspace_id
                 AND cc.domain = LOWER(c.domain)
             )
           LIMIT :maxRows
           """, nativeQuery = true)
    List<WorkspaceDomain> findCompaniesMissingCrawl(@Param("maxRows") int maxRows);
}
