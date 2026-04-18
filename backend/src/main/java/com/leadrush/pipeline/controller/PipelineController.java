package com.leadrush.pipeline.controller;

import com.leadrush.common.ApiResponse;
import com.leadrush.pipeline.dto.*;
import com.leadrush.pipeline.service.DealService;
import com.leadrush.pipeline.service.PipelineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final DealService dealService;

    @GetMapping
    public ApiResponse<List<PipelineResponse>> listPipelines() {
        return ApiResponse.success(pipelineService.listPipelines());
    }

    @GetMapping("/{id}")
    public ApiResponse<PipelineResponse> getPipeline(@PathVariable UUID id) {
        return ApiResponse.success(pipelineService.getPipeline(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PipelineResponse>> createPipeline(
            @Valid @RequestBody CreatePipelineRequest request) {
        PipelineResponse pipeline = pipelineService.createPipeline(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(pipeline));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePipeline(@PathVariable UUID id) {
        pipelineService.deletePipeline(id);
        return ApiResponse.success("Pipeline deleted");
    }

    // ── Stage management ──

    @PostMapping("/{id}/stages")
    public ApiResponse<PipelineResponse> addStage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStageRequest request) {
        return ApiResponse.success(pipelineService.addStage(id, request));
    }

    @DeleteMapping("/{id}/stages/{stageId}")
    public ApiResponse<Void> deleteStage(@PathVariable UUID id, @PathVariable UUID stageId) {
        pipelineService.deleteStage(id, stageId);
        return ApiResponse.success("Stage deleted");
    }

    // ── Deals within a pipeline (Kanban data) ──

    @GetMapping("/{id}/deals")
    public ApiResponse<List<DealResponse>> listDeals(@PathVariable UUID id) {
        return ApiResponse.success(dealService.listDealsByPipeline(id));
    }
}
