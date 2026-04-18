package com.leadrush.landingpage.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.landingpage.dto.CreateLandingPageRequest;
import com.leadrush.landingpage.dto.LandingPageResponse;
import com.leadrush.landingpage.service.LandingPageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/landing-pages")
@RequiredArgsConstructor
public class LandingPageController {

    private final LandingPageService pageService;

    @GetMapping
    public ApiResponse<List<LandingPageResponse>> listPages() {
        return ApiResponse.success(pageService.listPages());
    }

    @GetMapping("/{id}")
    public ApiResponse<LandingPageResponse> getPage(@PathVariable UUID id) {
        return ApiResponse.success(pageService.getPage(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LandingPageResponse>> createPage(
            @Valid @RequestBody CreateLandingPageRequest request) {
        LandingPageResponse page = pageService.createPage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(page));
    }

    @PutMapping("/{id}")
    public ApiResponse<LandingPageResponse> updatePage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateLandingPageRequest request) {
        return ApiResponse.success(pageService.updatePage(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<LandingPageResponse> publishPage(@PathVariable UUID id) {
        return ApiResponse.success(pageService.publishPage(id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<LandingPageResponse> unpublishPage(@PathVariable UUID id) {
        return ApiResponse.success(pageService.unpublishPage(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePage(@PathVariable UUID id) {
        pageService.deletePage(id);
        return ApiResponse.success("Landing page deleted");
    }
}
