package com.leadrush.landingpage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.landingpage.dto.*;
import com.leadrush.landingpage.entity.Form;
import com.leadrush.landingpage.entity.LandingPage;
import com.leadrush.landingpage.repository.FormRepository;
import com.leadrush.landingpage.repository.LandingPageRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LandingPageService {

    private final LandingPageRepository pageRepository;
    private final FormRepository formRepository;
    private final FormService formService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public List<LandingPageResponse> listPages() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return pageRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LandingPageResponse getPage(UUID id) {
        return toResponse(findPage(id));
    }

    @Transactional
    public LandingPageResponse createPage(CreateLandingPageRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        String slug = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug()
                : generateSlug(request.getName());

        LandingPage page = LandingPage.builder()
                .name(request.getName())
                .slug(slug)
                .metaTitle(request.getMetaTitle())
                .metaDescription(request.getMetaDescription())
                .blocks(request.getBlocks() != null ? request.getBlocks() : "[]")
                .status(LandingPage.Status.DRAFT)
                .build();
        page.setWorkspaceId(workspaceId);

        page = pageRepository.save(page);
        log.info("Landing page created: {} (slug: {})", page.getName(), page.getSlug());
        return toResponse(page);
    }

    @Transactional
    public LandingPageResponse updatePage(UUID id, CreateLandingPageRequest request) {
        LandingPage page = findPage(id);
        page.setName(request.getName());
        if (request.getSlug() != null && !request.getSlug().isBlank()) page.setSlug(request.getSlug());
        page.setMetaTitle(request.getMetaTitle());
        page.setMetaDescription(request.getMetaDescription());
        if (request.getBlocks() != null) page.setBlocks(request.getBlocks());
        return toResponse(pageRepository.save(page));
    }

    @Transactional
    public LandingPageResponse publishPage(UUID id) {
        LandingPage page = findPage(id);
        page.setStatus(LandingPage.Status.PUBLISHED);
        page.setPublishedAt(LocalDateTime.now());
        return toResponse(pageRepository.save(page));
    }

    @Transactional
    public LandingPageResponse unpublishPage(UUID id) {
        LandingPage page = findPage(id);
        page.setStatus(LandingPage.Status.DRAFT);
        return toResponse(pageRepository.save(page));
    }

    @Transactional
    public void deletePage(UUID id) {
        pageRepository.delete(findPage(id));
    }

    // ── PUBLIC rendering ──

    /**
     * Look up a PUBLISHED page by slug — no authentication required.
     * Also fetches any forms referenced by form blocks so the renderer has everything it needs.
     */
    @Transactional
    public PublicPageResponse renderBySlug(String slug) {
        LandingPage page = pageRepository.findBySlugAndStatus(slug, LandingPage.Status.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Landing page not found"));

        // Async-ish: increment view count (atomic UPDATE — cheap)
        pageRepository.incrementViewCount(page.getId());

        // Extract form IDs from blocks
        List<UUID> formIds = extractFormIdsFromBlocks(page.getBlocks());

        // Hydrate forms so the renderer can draw the fields
        List<FormResponse> forms = formIds.stream()
                .map(formId -> formRepository.findById(formId)
                        .filter(f -> f.getWorkspaceId().equals(page.getWorkspaceId()))
                        .orElse(null))
                .filter(f -> f != null)
                .map(this::formToResponse)
                .toList();

        return PublicPageResponse.builder()
                .name(page.getName())
                .slug(page.getSlug())
                .metaTitle(page.getMetaTitle())
                .metaDescription(page.getMetaDescription())
                .blocks(page.getBlocks())
                .forms(forms)
                .build();
    }

    /**
     * Called by FormSubmissionService after a successful submission.
     * Increments the page's conversion counter.
     */
    @Transactional
    public void recordConversion(UUID pageId) {
        pageRepository.incrementConversionCount(pageId);
    }

    // ── Helpers ──

    public LandingPage findPage(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return pageRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("LandingPage", id));
    }

    /** URL-friendly slug generator. "My Awesome Page" → "my-awesome-page". */
    private String generateSlug(String name) {
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isEmpty()) base = "page";

        // Append random suffix if base is taken globally
        String slug = base;
        int suffix = 0;
        while (pageRepository.findBySlugAndStatus(slug, LandingPage.Status.PUBLISHED).isPresent()
                || pageRepository.findBySlugAndStatus(slug, LandingPage.Status.DRAFT).isPresent()) {
            suffix++;
            slug = base + "-" + suffix;
            if (suffix > 99) {
                slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
                break;
            }
        }
        return slug;
    }

    /** Walks the blocks JSON array and extracts form IDs from form-type blocks. */
    private List<UUID> extractFormIdsFromBlocks(String blocksJson) {
        List<UUID> ids = new ArrayList<>();
        if (blocksJson == null) return ids;
        try {
            JsonNode root = objectMapper.readTree(blocksJson);
            if (!root.isArray()) return ids;
            for (JsonNode block : root) {
                JsonNode type = block.get("type");
                JsonNode props = block.get("props");
                if (type != null && "form".equalsIgnoreCase(type.asText())
                        && props != null && props.has("formId") && !props.get("formId").isNull()) {
                    try {
                        ids.add(UUID.fromString(props.get("formId").asText()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse blocks JSON: {}", e.getMessage());
        }
        return ids;
    }

    private LandingPageResponse toResponse(LandingPage page) {
        return LandingPageResponse.builder()
                .id(page.getId())
                .name(page.getName())
                .slug(page.getSlug())
                .metaTitle(page.getMetaTitle())
                .metaDescription(page.getMetaDescription())
                .blocks(page.getBlocks())
                .status(page.getStatus().name())
                .publishedAt(page.getPublishedAt())
                .viewCount(page.getViewCount())
                .conversionCount(page.getConversionCount())
                .createdAt(page.getCreatedAt())
                .updatedAt(page.getUpdatedAt())
                .build();
    }

    private FormResponse formToResponse(Form f) {
        return FormResponse.builder()
                .id(f.getId())
                .name(f.getName())
                .description(f.getDescription())
                .fields(f.getFields())
                .successRedirectUrl(f.getSuccessRedirectUrl())
                .successMessage(f.getSuccessMessage())
                .autoEnrollSequenceId(f.getAutoEnrollSequenceId())
                .build();
    }
}
