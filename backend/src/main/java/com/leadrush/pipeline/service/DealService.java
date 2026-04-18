package com.leadrush.pipeline.service;

import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.pipeline.dto.*;
import com.leadrush.pipeline.entity.Deal;
import com.leadrush.pipeline.entity.Pipeline;
import com.leadrush.pipeline.entity.PipelineStage;
import com.leadrush.pipeline.repository.DealRepository;
import com.leadrush.pipeline.repository.PipelineStageRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DealService {

    private final DealRepository dealRepository;
    private final PipelineStageRepository stageRepository;
    private final ContactRepository contactRepository;
    private final PipelineService pipelineService;
    private final WebhookService webhookService;

    @Transactional(readOnly = true)
    public List<DealResponse> listDealsByPipeline(UUID pipelineId) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        pipelineService.findPipeline(pipelineId);   // authz check
        return dealRepository.findByWorkspaceIdAndPipelineIdOrderByCreatedAtDesc(workspaceId, pipelineId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DealResponse> listDealsByContact(UUID contactId) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return dealRepository.findByWorkspaceIdAndContactsId(workspaceId, contactId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DealResponse getDeal(UUID id) {
        return toResponse(findDeal(id));
    }

    @Transactional
    public DealResponse createDeal(CreateDealRequest request) {
        UUID workspaceId = TenantContext.getWorkspaceId();

        Pipeline pipeline = pipelineService.findPipeline(request.getPipelineId());

        // Determine stage — use provided OR first stage of pipeline
        PipelineStage stage;
        if (request.getPipelineStageId() != null) {
            stage = findStageInPipeline(pipeline, request.getPipelineStageId());
        } else {
            stage = pipeline.getStages().stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Pipeline has no stages"));
        }

        Deal deal = Deal.builder()
                .pipelineId(pipeline.getId())
                .stage(stage)
                .name(request.getName())
                .description(request.getDescription())
                .valueAmount(request.getValueAmount())
                .valueCurrency(request.getValueCurrency() != null ? request.getValueCurrency() : "USD")
                .expectedCloseAt(request.getExpectedCloseAt())
                .ownerUserId(TenantContext.getUserId())
                .build();
        deal.setWorkspaceId(workspaceId);

        // Link contacts
        if (request.getContactIds() != null) {
            Set<Contact> contacts = new HashSet<>();
            for (UUID cid : request.getContactIds()) {
                contactRepository.findByIdAndWorkspaceId(cid, workspaceId)
                        .ifPresent(contacts::add);
            }
            deal.setContacts(contacts);
        }

        deal = dealRepository.save(deal);

        // If new stage is WON/LOST, mark closed
        updateClosedAt(deal);

        log.info("Deal created: {} (id: {}, stage: {})", deal.getName(), deal.getId(), stage.getName());

        webhookService.publish(WebhookEventType.DEAL_CREATED, dealPayload(deal));

        return toResponse(deal);
    }

    @Transactional
    public DealResponse updateDeal(UUID id, UpdateDealRequest request) {
        Deal deal = findDeal(id);

        if (request.getName() != null) deal.setName(request.getName());
        if (request.getDescription() != null) deal.setDescription(request.getDescription());
        if (request.getValueAmount() != null) deal.setValueAmount(request.getValueAmount());
        if (request.getValueCurrency() != null) deal.setValueCurrency(request.getValueCurrency());
        if (request.getExpectedCloseAt() != null) deal.setExpectedCloseAt(request.getExpectedCloseAt());

        // Stage change = "move" operation
        if (request.getPipelineStageId() != null) {
            Pipeline pipeline = pipelineService.findPipeline(deal.getPipelineId());
            PipelineStage newStage = findStageInPipeline(pipeline, request.getPipelineStageId());
            deal.setStage(newStage);
            updateClosedAt(deal);
        }

        deal = dealRepository.save(deal);
        return toResponse(deal);
    }

    /**
     * Move a deal to a different stage — dedicated endpoint for drag-and-drop on Kanban.
     */
    @Transactional
    public DealResponse moveDeal(UUID dealId, UUID newStageId) {
        Deal deal = findDeal(dealId);
        Pipeline pipeline = pipelineService.findPipeline(deal.getPipelineId());
        PipelineStage newStage = findStageInPipeline(pipeline, newStageId);

        PipelineStage previousStage = deal.getStage();
        deal.setStage(newStage);
        updateClosedAt(deal);

        deal = dealRepository.save(deal);
        log.info("Deal {} moved to stage: {}", dealId, newStage.getName());

        // Always fire deal.moved; if we landed on WON/LOST, fire those too.
        java.util.Map<String, Object> payload = dealPayload(deal);
        payload = new java.util.LinkedHashMap<>(payload);
        payload.put("previousStageId", previousStage != null ? previousStage.getId() : null);
        payload.put("previousStageName", previousStage != null ? previousStage.getName() : null);
        webhookService.publish(WebhookEventType.DEAL_MOVED, payload);

        PipelineStage.StageType type = newStage.getStageType();
        if (type == PipelineStage.StageType.WON) {
            webhookService.publish(WebhookEventType.DEAL_WON, dealPayload(deal));
        } else if (type == PipelineStage.StageType.LOST) {
            webhookService.publish(WebhookEventType.DEAL_LOST, dealPayload(deal));
        }

        return toResponse(deal);
    }

    @Transactional
    public void deleteDeal(UUID id) {
        Deal deal = findDeal(id);
        dealRepository.delete(deal);
    }

    // ── Helpers ──

    private void updateClosedAt(Deal deal) {
        PipelineStage.StageType type = deal.getStage().getStageType();
        if (type == PipelineStage.StageType.WON || type == PipelineStage.StageType.LOST) {
            if (deal.getClosedAt() == null) {
                deal.setClosedAt(LocalDateTime.now());
            }
        } else {
            deal.setClosedAt(null);
        }
    }

    /** Shared payload for all deal.* webhook events. */
    private java.util.Map<String, Object> dealPayload(Deal deal) {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("dealId", deal.getId());
        out.put("name", deal.getName());
        out.put("pipelineId", deal.getPipelineId());
        out.put("stageId", deal.getStage().getId());
        out.put("stageName", deal.getStage().getName());
        out.put("stageType", deal.getStage().getStageType().name());
        out.put("valueAmount", deal.getValueAmount());
        out.put("valueCurrency", deal.getValueCurrency());
        out.put("ownerUserId", deal.getOwnerUserId());
        out.put("expectedCloseAt", deal.getExpectedCloseAt());
        out.put("closedAt", deal.getClosedAt());
        return out;
    }

    private PipelineStage findStageInPipeline(Pipeline pipeline, UUID stageId) {
        return pipeline.getStages().stream()
                .filter(s -> s.getId().equals(stageId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Stage does not belong to this pipeline"));
    }

    private Deal findDeal(UUID id) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return dealRepository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));
    }

    private DealResponse toResponse(Deal deal) {
        return DealResponse.builder()
                .id(deal.getId())
                .name(deal.getName())
                .description(deal.getDescription())
                .pipelineId(deal.getPipelineId())
                .pipelineStageId(deal.getStage().getId())
                .stageName(deal.getStage().getName())
                .valueAmount(deal.getValueAmount())
                .valueCurrency(deal.getValueCurrency())
                .ownerUserId(deal.getOwnerUserId())
                .expectedCloseAt(deal.getExpectedCloseAt())
                .closedAt(deal.getClosedAt())
                .contacts(deal.getContacts().stream().map(c ->
                        DealResponse.ContactSummary.builder()
                                .id(c.getId())
                                .fullName(c.getFullName())
                                .primaryEmail(c.getPrimaryEmail())
                                .build()
                ).toList())
                .createdAt(deal.getCreatedAt())
                .updatedAt(deal.getUpdatedAt())
                .build();
    }
}
