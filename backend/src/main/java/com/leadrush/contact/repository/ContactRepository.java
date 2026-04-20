package com.leadrush.contact.repository;

import com.leadrush.contact.entity.Contact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ContactRepository extends JpaRepository<Contact, UUID>,
                                           JpaSpecificationExecutor<Contact> {

    Page<Contact> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    Optional<Contact> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    // Extension dedupe hits this on every LinkedIn import — case-insensitive match.
    Optional<Contact> findFirstByWorkspaceIdAndLinkedinUrlIgnoreCase(UUID workspaceId, String linkedinUrl);

    long countByWorkspaceId(UUID workspaceId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(c) FROM Contact c WHERE c.workspaceId = :workspaceId AND c.createdAt >= :since"
    )
    long countByWorkspaceIdAndCreatedAtAfter(
        @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId,
        @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since
    );

    // Daily counts since cutoff — rows of [java.sql.Date, Long]. FUNCTION('DATE', ...)
    // emits the Postgres DATE() truncation.
    @org.springframework.data.jpa.repository.Query("""
        SELECT FUNCTION('DATE', c.createdAt), COUNT(c)
        FROM Contact c
        WHERE c.workspaceId = :workspaceId AND c.createdAt >= :since
        GROUP BY FUNCTION('DATE', c.createdAt)
        ORDER BY FUNCTION('DATE', c.createdAt) ASC
    """)
    java.util.List<Object[]> dailyContactCounts(
        @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId,
        @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT c.lifecycleStage, COUNT(c)
        FROM Contact c
        WHERE c.workspaceId = :workspaceId
        GROUP BY c.lifecycleStage
    """)
    java.util.List<Object[]> lifecycleDistribution(
        @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId
    );

    /**
     * Fuzzy name match used by the extension's duplicate-detector. Matches when
     * both firstName and lastName prefix-match (case-insensitive) — we skip
     * substring matching for the last name to avoid pairing "Andrea" with
     * "Andrea Smith-Andrews". A LIKE with leading wildcard kills the index so
     * keep this bounded to distinct name pairs.
     *
     * Excludes contacts that already have the incoming LinkedIn URL (they'd have
     * matched the exact linkedinUrl path and never reached this query).
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT c FROM Contact c
        WHERE c.workspaceId = :workspaceId
          AND LOWER(c.firstName) = LOWER(:firstName)
          AND (
            :lastName IS NULL
            OR c.lastName IS NULL
            OR LOWER(c.lastName) = LOWER(:lastName)
          )
          AND (c.linkedinUrl IS NULL OR LOWER(c.linkedinUrl) <> LOWER(:excludeLinkedinUrl))
        ORDER BY c.updatedAt DESC
    """)
    java.util.List<Contact> findPossibleMatches(
        @org.springframework.data.repository.query.Param("workspaceId") UUID workspaceId,
        @org.springframework.data.repository.query.Param("firstName") String firstName,
        @org.springframework.data.repository.query.Param("lastName") String lastName,
        @org.springframework.data.repository.query.Param("excludeLinkedinUrl") String excludeLinkedinUrl,
        Pageable pageable
    );
}
