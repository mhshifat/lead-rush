package com.leadrush.leadscoring.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.leadscoring.dto.AdjustScoreRequest;
import com.leadrush.leadscoring.dto.LeadScoreLogResponse;
import com.leadrush.leadscoring.dto.LeadScoreRuleRequest;
import com.leadrush.leadscoring.dto.LeadScoreRuleResponse;
import com.leadrush.leadscoring.service.LeadScoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lead-score")
@RequiredArgsConstructor
public class LeadScoreController {

    private final LeadScoringService leadScoringService;

    // ── Rules CRUD ──

    @GetMapping("/rules")
    public ApiResponse<List<LeadScoreRuleResponse>> listRules() {
        return ApiResponse.success(leadScoringService.listRules());
    }

    @PostMapping("/rules")
    public ApiResponse<LeadScoreRuleResponse> createRule(@Valid @RequestBody LeadScoreRuleRequest request) {
        return ApiResponse.success(leadScoringService.createRule(request));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<LeadScoreRuleResponse> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody LeadScoreRuleRequest request
    ) {
        return ApiResponse.success(leadScoringService.updateRule(id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> deleteRule(@PathVariable UUID id) {
        leadScoringService.deleteRule(id);
        return ApiResponse.success(null);
    }

    // ── Score actions ──

    /** Wipe every contact's score and replay CONTACT_CREATED rules — see LeadScoringService. */
    @PostMapping("/recalculate")
    public ApiResponse<Map<String, Integer>> recalculate() {
        int processed = leadScoringService.recalculateAll();
        return ApiResponse.success(Map.of("contactsProcessed", processed));
    }

    /** Manual one-off adjustment for a specific contact. */
    @PostMapping("/contacts/{contactId}/adjust")
    public ApiResponse<Void> adjust(
            @PathVariable UUID contactId,
            @Valid @RequestBody AdjustScoreRequest request
    ) {
        leadScoringService.adjustScore(contactId, request.getPointsDelta(), request.getReason());
        return ApiResponse.success(null);
    }

    // ── History ──

    @GetMapping("/contacts/{contactId}/history")
    public ApiResponse<List<LeadScoreLogResponse>> getHistory(@PathVariable UUID contactId) {
        return ApiResponse.success(leadScoringService.getHistory(contactId));
    }
}
