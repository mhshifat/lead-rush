package com.leadrush.landingpage.repository;

import com.leadrush.landingpage.entity.LandingPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LandingPageRepository extends JpaRepository<LandingPage, UUID> {

    List<LandingPage> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<LandingPage> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    /** Public lookup by slug — only returns PUBLISHED pages. */
    Optional<LandingPage> findBySlugAndStatus(String slug, LandingPage.Status status);

    /** All published pages — used by the public sitemap. Cross-tenant on purpose. */
    List<LandingPage> findByStatusOrderByPublishedAtDesc(LandingPage.Status status);

    /** Atomic view count increment. */
    @Modifying
    @Query("UPDATE LandingPage p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") UUID id);

    /** Atomic conversion count increment. */
    @Modifying
    @Query("UPDATE LandingPage p SET p.conversionCount = p.conversionCount + 1 WHERE p.id = :id")
    void incrementConversionCount(@Param("id") UUID id);
}
