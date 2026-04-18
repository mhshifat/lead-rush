package com.leadrush.extension.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.extension.dto.ExtensionTaskResponse;
import com.leadrush.extension.dto.LinkedInImportRequest;
import com.leadrush.extension.dto.LinkedInImportResponse;
import com.leadrush.extension.dto.MeResponse;
import com.leadrush.extension.service.ExtensionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints the browser extension hits with X-API-Key auth.
 *
 * Deliberately narrow — the extension shouldn't get the full CRUD surface.
 * Each endpoint either reads or performs one specific action the extension needs.
 */
@RestController
@RequestMapping("/api/v1/ext")
@RequiredArgsConstructor
public class ExtensionController {

    private final ExtensionService extensionService;

    /** Popup "Connected to..." header. */
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.success(extensionService.getMe());
    }

    /** Popup task list + badge count. */
    @GetMapping("/tasks")
    public ApiResponse<List<ExtensionTaskResponse>> listTasks() {
        return ApiResponse.success(extensionService.listPendingLinkedInTasks());
    }

    /** Side-panel lookup when the user lands on a profile page. */
    @GetMapping("/tasks/by-linkedin-url")
    public ApiResponse<List<ExtensionTaskResponse>> tasksForUrl(@RequestParam("url") String url) {
        return ApiResponse.success(extensionService.listPendingTasksForLinkedInUrl(url));
    }

    /** Called after the user actually sends the message/connection on LinkedIn. */
    @PostMapping("/tasks/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable UUID id) {
        extensionService.completeTask(id);
        return ApiResponse.success(null);
    }

    /** Find-or-create a contact from scraped profile data. */
    @PostMapping("/contacts/from-linkedin")
    public ApiResponse<LinkedInImportResponse> importFromLinkedIn(
            @Valid @RequestBody LinkedInImportRequest request
    ) {
        return ApiResponse.success(extensionService.importFromLinkedIn(request));
    }
}
