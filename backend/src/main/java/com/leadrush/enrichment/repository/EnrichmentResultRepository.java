package com.leadrush.enrichment.repository;

import com.leadrush.enrichment.entity.EnrichmentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrichmentResultRepository extends JpaRepository<EnrichmentResult, UUID> {

    List<EnrichmentResult> findByContactIdOrderByEnrichedAtDesc(UUID contactId);

    /**
     * Get the most recent result for a given (contact, provider) pair.
     * Used to check if we have a fresh cached result before calling the API.
     */
    @Query("""
        SELECT r FROM EnrichmentResult r
        WHERE r.contactId = :contactId
        AND r.providerKey = :providerKey
        ORDER BY r.enrichedAt DESC
        LIMIT 1
    """)
    Optional<EnrichmentResult> findLatestForContactAndProvider(
            @Param("contactId") UUID contactId,
            @Param("providerKey") String providerKey);
}
