package com.leadrush.pipeline.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.pipeline.dto.CreateDealRequest;
import com.leadrush.pipeline.dto.DealResponse;
import com.leadrush.pipeline.dto.UpdateDealRequest;
import com.leadrush.pipeline.service.DealService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
public class DealController {

    private final DealService dealService;

    @GetMapping("/{id}")
    public ApiResponse<DealResponse> getDeal(@PathVariable UUID id) {
        return ApiResponse.success(dealService.getDeal(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(@Valid @RequestBody CreateDealRequest request) {
        DealResponse deal = dealService.createDeal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(deal));
    }

    @PutMapping("/{id}")
    public ApiResponse<DealResponse> updateDeal(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDealRequest request) {
        return ApiResponse.success(dealService.updateDeal(id, request));
    }

    /**
     * POST /api/v1/deals/{id}/move
     * Dedicated endpoint for Kanban drag-and-drop. Just moves the deal to a new stage.
     */
    @PostMapping("/{id}/move")
    public ApiResponse<DealResponse> moveDeal(
            @PathVariable UUID id,
            @RequestBody MoveDealRequest request) {
        return ApiResponse.success(dealService.moveDeal(id, request.getPipelineStageId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDeal(@PathVariable UUID id) {
        dealService.deleteDeal(id);
        return ApiResponse.success("Deal deleted");
    }

    @Data
    public static class MoveDealRequest {
        private UUID pipelineStageId;
    }
}
