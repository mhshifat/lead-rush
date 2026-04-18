package com.leadrush.pipeline.service;

import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.pipeline.dto.*;
import com.leadrush.pipeline.entity.Pipeline;
import com.leadrush.pipeline.entity.PipelineStage;
import com.leadrush.pipeline.repository.PipelineRepository;
import com.leadrush.pipeline.repository.PipelineStageRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final PipelineStageRepository stageRepository;

    @Transactional(readOnly = true)
    public List<PipelineResponse> listPipelines() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return pipelineRepository.findByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineResponse getPipeline(UUID id) {
        return toResponse(findPipeline(id));
    }

    /**
     * Create a new pipeline. If no pipelines exist yet, this one becomes the default.
     * Also creates 3 sensible default stages (Discovery / Proposal / Closed Won).
     */
    @Transactional
    public PipelineResponse createPipeline(CreatePipelineRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        boolean isFirst = pipelineRepository.findByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).isEmpty();

        Pipeline pipeline = Pipeline.builder()
                .name(request.getName())
                .description(request.getDescription())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : isFirst)
                .build();
        pipeline.setWorkspaceId(workspaceId);

        // If this is set as default, un-default the others
        if (pipeline.isDefault()) {
            pipelineRepository.findByWorkspaceIdOrderByDisplayOrderAsc(workspaceId)
                    .forEach(p -> {
                        if (p.isDefault()) {
                            p.setDefault(false);
                            pipelineRepository.save(p);
                        }
                    });
        }

        pipeline = pipelineRepository.save(pipeline);

        // Seed with default stages
        addStageInternal(pipeline, "Discovery", "#3B82F6", 10, 0, PipelineStage.StageType.OPEN);
        addStageInternal(pipeline, "Proposal", "#F59E0B", 50, 1, PipelineStage.StageType.OPEN);
        addStageInternal(pipeline, "Closed Won", "#10B981", 100, 2, PipelineStage.StageType.WON);
        addStageInternal(pipeline, "Closed Lost", "#EF4444", 0, 3, PipelineStage.StageType.LOST);

        log.info("Pipeline created: {} (id: {})", pipeline.getName(), pipeline.getId());
        return toResponse(pipeline);
    }

    @Transactional
    public void deletePipeline(UUID id) {
        Pipeline pipeline = findPipeline(id);
        pipelineRepository.delete(pipeline);
    }

    // ── Stage management ──

    @Transactional
    public PipelineResponse addStage(UUID pipelineId, CreateStageRequest request) {
        Pipeline pipeline = findPipeline(pipelineId);

        int nextOrder = pipeline.getStages().stream()
                .mapToInt(PipelineStage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        addStageInternal(pipeline,
                request.getName(),
                request.getColor(),
                request.getWinProbability() != null ? request.getWinProbability() : 0,
                nextOrder,
                request.getStageType() != null
                        ? PipelineStage.StageType.valueOf(request.getStageType())
                        : PipelineStage.StageType.OPEN);

        pipelineRepository.save(pipeline);
        return toResponse(pipeline);
    }

    @Transactional
    public void deleteStage(UUID pipelineId, UUID stageId) {
        Pipeline pipeline = findPipeline(pipelineId);
        if (pipeline.getStages().size() <= 1) {
            throw new BusinessException("Pipeline must have at least one stage");
        }
        pipeline.getStages().removeIf(s -> s.getId().equals(stageId));
        pipelineRepository.save(pipeline);
    }

    // ── Internal helpers ──

    private void addStageInternal(
            Pipeline pipeline,
            String name,
            String color,
            int probability,
            int order,
            PipelineStage.StageType type
    ) {
        PipelineStage stage = PipelineStage.builder()
                .name(name)
                .color(color)
                .winProbability(probability)
                .displayOrder(order)
                .stageType(type)
                .build();
        stage.setWorkspaceId(pipeline.getWorkspaceId());
        pipeline.addStage(stage);
    }

    /** Entity loader — also used by DealService. */
    public Pipeline findPipeline(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return pipelineRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id));
    }

    private PipelineResponse toResponse(Pipeline pipeline) {
        return PipelineResponse.builder()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .description(pipeline.getDescription())
                .isDefault(pipeline.isDefault())
                .displayOrder(pipeline.getDisplayOrder())
                .stages(pipeline.getStages().stream().map(s ->
                        PipelineResponse.StageResponse.builder()
                                .id(s.getId())
                                .name(s.getName())
                                .color(s.getColor())
                                .winProbability(s.getWinProbability())
                                .displayOrder(s.getDisplayOrder())
                                .stageType(s.getStageType().name())
                                .build()
                ).toList())
                .createdAt(pipeline.getCreatedAt())
                .updatedAt(pipeline.getUpdatedAt())
                .build();
    }
}
