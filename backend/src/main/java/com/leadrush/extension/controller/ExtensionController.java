package com.leadrush.extension.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.extension.dto.ExtensionTaskResponse;
import com.leadrush.extension.dto.LinkedInImportRequest;
import com.leadrush.extension.dto.LinkedInImportResponse;
import com.leadrush.extension.dto.MeResponse;
import com.leadrush.extension.dto.OpenerRequest;
import com.leadrush.extension.dto.OpenerResponse;
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

    /**
     * Does this LinkedIn URL already match a contact? Used by the side panel to
     * show an "Already in Lead Rush" card instead of a blank import button.
     * Returns null in the data envelope if there's no match.
     */
    @GetMapping("/contacts/by-linkedin-url")
    public ApiResponse<com.leadrush.extension.dto.ContactLookupResponse> lookupContact(
            @RequestParam("url") String url
    ) {
        return ApiResponse.success(extensionService.lookupByLinkedInUrl(url));
    }

    /**
     * Lookup by email — used by the Gmail sidebar to resolve the open thread's
     * participants to Lead Rush contacts. Returns null in the envelope if the
     * address isn't on any contact in this workspace.
     */
    @GetMapping("/contacts/by-email")
    public ApiResponse<com.leadrush.extension.dto.ContactLookupResponse> lookupByEmail(
            @RequestParam("email") String email
    ) {
        return ApiResponse.success(extensionService.lookupByEmail(email));
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

    /**
     * Dry-run duplicate check — returns contacts with similar name/company
     * that DON'T yet have this LinkedIn URL attached. The panel uses this to
     * offer "Merge into existing contact" before creating a sibling record.
     */
    @PostMapping("/contacts/possible-matches")
    public ApiResponse<List<com.leadrush.extension.dto.PossibleMatchResponse>> possibleMatches(
            @Valid @RequestBody LinkedInImportRequest request
    ) {
        return ApiResponse.success(extensionService.findPossibleMatches(request));
    }

    /** Active sequences, minimal shape for the panel's enroll picker. */
    @GetMapping("/sequences")
    public ApiResponse<List<com.leadrush.extension.dto.ExtensionSequenceResponse>> listSequences() {
        return ApiResponse.success(extensionService.listActiveSequences());
    }

    /** Enroll an imported contact into a sequence directly from the LinkedIn panel. */
    @PostMapping("/enrollments")
    public ApiResponse<com.leadrush.extension.dto.ExtensionEnrollResponse> enroll(
            @Valid @RequestBody com.leadrush.extension.dto.ExtensionEnrollRequest request
    ) {
        return ApiResponse.success(extensionService.enroll(request.getSequenceId(), request.getContactId()));
    }

    /** Drop a freeform note against a contact — persisted as a completed NOTE task. */
    @PostMapping("/contacts/{id}/notes")
    public ApiResponse<Void> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody com.leadrush.extension.dto.ExtensionNoteRequest request
    ) {
        extensionService.addNote(id, request.getBody());
        return ApiResponse.success(null);
    }

    /**
     * Tell us when scraper selectors miss. No PII — just layout + URL + field list.
     * Logged as WARN so ops can grep for trends (e.g. a spike after LinkedIn ships a DOM change).
     */
    @PostMapping("/telemetry/scraper")
    public ApiResponse<Void> scraperTelemetry(
            @Valid @RequestBody com.leadrush.extension.dto.ScraperTelemetryRequest request
    ) {
        extensionService.recordScraperMiss(request);
        return ApiResponse.success(null);
    }

    // ── Saved searches ──

    @GetMapping("/saved-searches")
    public ApiResponse<List<com.leadrush.extension.dto.SavedSearchResponse>> listSavedSearches() {
        return ApiResponse.success(extensionService.listSavedSearches());
    }

    @PostMapping("/saved-searches")
    public ApiResponse<com.leadrush.extension.dto.SavedSearchResponse> saveSearch(
            @Valid @RequestBody com.leadrush.extension.dto.SaveSearchRequest request
    ) {
        return ApiResponse.success(extensionService.saveSearch(request));
    }

    /**
     * Called on every mount of the side panel on a LinkedIn search page. The
     * panel sends the currently-visible profile URLs; the backend returns
     * which are new since the last check and silently updates the known-set.
     */
    @PostMapping("/saved-searches/check")
    public ApiResponse<com.leadrush.extension.dto.CheckSearchResponse> checkSavedSearch(
            @Valid @RequestBody com.leadrush.extension.dto.CheckSearchRequest request
    ) {
        return ApiResponse.success(extensionService.checkSavedSearch(request));
    }

    @DeleteMapping("/saved-searches/{id}")
    public ApiResponse<Void> deleteSavedSearch(@PathVariable UUID id) {
        extensionService.deleteSavedSearch(id);
        return ApiResponse.success(null);
    }

    /**
     * AI-generated opener (LinkedIn note or email body) from scraped profile data.
     * Kept scoped to the extension so the side panel's "✨ Generate opener" button
     * can call it without re-using the broader /ai/generate-email machinery (which
     * expects an already-imported contact).
     */
    @PostMapping("/ai/opener")
    public ApiResponse<OpenerResponse> generateOpener(@Valid @RequestBody OpenerRequest request) {
        return ApiResponse.success(extensionService.generateOpener(request));
    }
}
