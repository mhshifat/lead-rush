package com.leadrush.landingpage.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.landingpage.dto.PublicPageResponse;
import com.leadrush.landingpage.dto.PublicSubmitRequest;
import com.leadrush.landingpage.service.FormSubmissionService;
import com.leadrush.landingpage.service.LandingPageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PUBLIC endpoints for landing page rendering and form submissions.
 * No JWT required. Mapped under /api/v1/public/** which is permitAll in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/public/pages")
@RequiredArgsConstructor
public class PublicPageController {

    private final LandingPageService pageService;
    private final FormSubmissionService submissionService;

    /**
     * GET /api/v1/public/pages/{slug}
     * Fetch a PUBLISHED landing page by slug — for the public renderer.
     */
    @GetMapping("/{slug}")
    public ApiResponse<PublicPageResponse> getBySlug(@PathVariable String slug) {
        return ApiResponse.success(pageService.renderBySlug(slug));
    }

    /**
     * POST /api/v1/public/pages/submit
     * Submit a form from a landing page or embedded widget.
     * Creates/updates contact, optionally enrolls in sequence, records attribution.
     */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<Map<String, String>>> submit(
            @Valid @RequestBody PublicSubmitRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        FormSubmissionService.SubmitResult result = submissionService.submit(request, ip, userAgent);

        Map<String, String> response = new java.util.HashMap<>();
        response.put("message", result.successMessage() != null
                ? result.successMessage()
                : "Submission received");
        if (result.successRedirectUrl() != null) {
            response.put("redirectUrl", result.successRedirectUrl());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
