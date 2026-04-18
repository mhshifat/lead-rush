package com.leadrush.analytics.service;

import com.leadrush.analytics.dto.*;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.repository.EmailSendLogRepository;
import com.leadrush.email.repository.MailboxRepository;
import com.leadrush.pipeline.entity.Pipeline;
import com.leadrush.pipeline.entity.PipelineStage;
import com.leadrush.pipeline.repository.DealRepository;
import com.leadrush.pipeline.repository.PipelineRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.sequence.entity.Sequence;
import com.leadrush.sequence.entity.SequenceStatus;
import com.leadrush.sequence.entity.SequenceStep;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceRepository;
import com.leadrush.sequence.repository.SequenceStepExecutionRepository;
import com.leadrush.task.entity.Task;
import com.leadrush.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Analytics service — aggregates metrics for dashboards and reports.
 *
 * Metrics are derived on-the-fly via aggregate COUNT queries.
 * For workspaces with 100k+ sends, we'd add a materialized view later.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ContactRepository contactRepository;
    private final SequenceRepository sequenceRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final SequenceStepExecutionRepository executionRepository;
    private final TaskRepository taskRepository;
    private final DealRepository dealRepository;
    private final PipelineRepository pipelineRepository;
    private final MailboxRepository mailboxRepository;
    private final EmailSendLogRepository emailSendLogRepository;

    // ── Overview ──

    @Transactional(readOnly = true)
    public DashboardOverview getOverview() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);

        long totalContacts = contactRepository.countByWorkspaceId(workspaceId);
        long contactsLast7 = contactRepository.countByWorkspaceIdAndCreatedAtAfter(workspaceId, sevenDaysAgo);

        long activeSequences = sequenceRepository.countByWorkspaceIdAndStatus(workspaceId, SequenceStatus.ACTIVE);
        long totalEnrollments = enrollmentRepository.countByWorkspaceId(workspaceId);

        long sent = executionRepository.countSentByWorkspaceSince(workspaceId, thirtyDaysAgo);
        long opened = executionRepository.countOpenedByWorkspaceSince(workspaceId, thirtyDaysAgo);
        long clicked = executionRepository.countClickedByWorkspaceSince(workspaceId, thirtyDaysAgo);
        long replied = executionRepository.countRepliedByWorkspaceSince(workspaceId, thirtyDaysAgo);

        long pendingTasks = taskRepository.countByWorkspaceIdAndStatus(workspaceId, Task.TaskStatus.PENDING);

        return DashboardOverview.builder()
                .totalContacts(totalContacts)
                .contactsAddedLast7Days(contactsLast7)
                .activeSequences(activeSequences)
                .totalEnrollments(totalEnrollments)
                .emailsSent(sent)
                .emailsOpened(opened)
                .emailsClicked(clicked)
                .emailsReplied(replied)
                .openRate(rate(opened, sent))
                .clickRate(rate(clicked, sent))
                .replyRate(rate(replied, sent))
                .pendingTasks(pendingTasks)
                .build();
    }

    @Transactional(readOnly = true)
    public List<SequenceAnalytics> getSequencePerformance() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        List<Sequence> sequences = sequenceRepository.findByWorkspaceId(workspaceId);

        return sequences.stream().map(seq -> {
            long sent = executionRepository.countSentBySequence(seq.getId());
            long opened = executionRepository.countOpenedBySequence(seq.getId());
            long clicked = executionRepository.countClickedBySequence(seq.getId());
            long replied = executionRepository.countRepliedBySequence(seq.getId());

            return SequenceAnalytics.builder()
                    .sequenceId(seq.getId())
                    .sequenceName(seq.getName())
                    .status(seq.getStatus().name())
                    .totalEnrolled(seq.getTotalEnrolled())
                    .activeEnrollments(enrollmentRepository.countBySequenceIdAndStatus(seq.getId(), EnrollmentStatus.ACTIVE))
                    .completedEnrollments(enrollmentRepository.countBySequenceIdAndStatus(seq.getId(), EnrollmentStatus.COMPLETED))
                    .unsubscribedEnrollments(enrollmentRepository.countBySequenceIdAndStatus(seq.getId(), EnrollmentStatus.UNSUBSCRIBED))
                    .emailsSent(sent)
                    .emailsOpened(opened)
                    .emailsClicked(clicked)
                    .emailsReplied(replied)
                    .openRate(rate(opened, sent))
                    .clickRate(rate(clicked, sent))
                    .replyRate(rate(replied, sent))
                    .build();
        }).toList();
    }

    // ── Sequence funnel ──

    /**
     * Per-step drop-off inside a sequence. Returns one row per step in stepOrder, with zeros
     * for steps the engine hasn't reached yet.
     */
    @Transactional(readOnly = true)
    public SequenceFunnel getSequenceFunnel(UUID sequenceId) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        Sequence seq = sequenceRepository.findByIdAndWorkspaceId(sequenceId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Sequence", sequenceId));

        // [stepId, sent, skipped, failed, opened, clicked, replied] per step
        Map<UUID, long[]> byStep = new HashMap<>();
        for (Object[] row : executionRepository.funnelBySequence(sequenceId)) {
            UUID stepId = (UUID) row[0];
            byStep.put(stepId, new long[] {
                    asLong(row[1]), asLong(row[2]), asLong(row[3]),
                    asLong(row[4]), asLong(row[5]), asLong(row[6])
            });
        }

        List<SequenceFunnel.Step> stepDtos = new ArrayList<>();
        int emailOrdinal = 0;
        for (SequenceStep step : seq.getSteps()) {
            long[] counts = byStep.getOrDefault(step.getId(), new long[6]);
            long sent = counts[0];
            long opened = counts[3];
            long clicked = counts[4];
            long replied = counts[5];

            String label = switch (step.getStepType()) {
                case EMAIL -> "Email " + (++emailOrdinal);
                case DELAY -> "Wait " + step.getDelayDays() + "d";
                case CALL -> "Call";
                case TASK -> "Task";
                case LINKEDIN_MESSAGE -> "LinkedIn msg";
                case LINKEDIN_CONNECT -> "LinkedIn connect";
            };

            stepDtos.add(SequenceFunnel.Step.builder()
                    .stepId(step.getId())
                    .stepOrder(step.getStepOrder())
                    .stepType(step.getStepType().name())
                    .label(label)
                    .sent(sent)
                    .opened(opened)
                    .clicked(clicked)
                    .replied(replied)
                    .skipped(counts[1])
                    .failed(counts[2])
                    .openRate(rate(opened, sent))
                    .clickRate(rate(clicked, sent))
                    .replyRate(rate(replied, sent))
                    .build());
        }

        return SequenceFunnel.builder()
                .sequenceId(seq.getId())
                .sequenceName(seq.getName())
                .steps(stepDtos)
                .build();
    }

    // ── Pipeline report ──

    @Transactional(readOnly = true)
    public List<PipelineReport> getPipelineReports() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        return pipelineRepository.findByWorkspaceIdOrderByDisplayOrderAsc(workspaceId).stream()
                .map(p -> buildPipelineReport(workspaceId, p))
                .toList();
    }

    private PipelineReport buildPipelineReport(UUID workspaceId, Pipeline pipeline) {
        Map<UUID, Long> countByStage = new HashMap<>();
        Map<UUID, BigDecimal> valueByStage = new HashMap<>();

        for (Object[] row : dealRepository.aggregateByStage(workspaceId, pipeline.getId())) {
            UUID stageId = (UUID) row[0];
            long count = asLong(row[1]);
            BigDecimal value = row[2] instanceof BigDecimal bd
                    ? bd
                    : BigDecimal.valueOf(((Number) row[2]).doubleValue());
            countByStage.put(stageId, count);
            valueByStage.put(stageId, value);
        }

        List<PipelineReport.StageBreakdown> stageDtos = new ArrayList<>();
        long totalDeals = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (PipelineStage stage : pipeline.getStages()) {
            long count = countByStage.getOrDefault(stage.getId(), 0L);
            BigDecimal value = valueByStage.getOrDefault(stage.getId(), BigDecimal.ZERO);
            totalDeals += count;
            totalValue = totalValue.add(value);
            stageDtos.add(PipelineReport.StageBreakdown.builder()
                    .stageId(stage.getId())
                    .stageName(stage.getName())
                    .stageOrder(stage.getDisplayOrder())
                    .color(stage.getColor())
                    .probability(stage.getWinProbability())
                    .dealCount(count)
                    .totalValue(value)
                    .build());
        }

        return PipelineReport.builder()
                .pipelineId(pipeline.getId())
                .pipelineName(pipeline.getName())
                .totalDeals(totalDeals)
                .totalValue(totalValue)
                .stages(stageDtos)
                .build();
    }

    // ── Contact growth ──

    @Transactional(readOnly = true)
    public ContactGrowth getContactGrowth(int windowDays) {
        UUID workspaceId = TenantContext.getWorkspaceId();
        int days = windowDays <= 0 ? 30 : Math.min(windowDays, 365);

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(days - 1);
        LocalDateTime since = start.atStartOfDay();

        Map<LocalDate, Long> byDate = new HashMap<>();
        for (Object[] row : contactRepository.dailyContactCounts(workspaceId, since)) {
            LocalDate date = row[0] instanceof Date sql ? sql.toLocalDate() : (LocalDate) row[0];
            byDate.put(date, asLong(row[1]));
        }

        List<ContactGrowth.DailyPoint> series = new ArrayList<>(days);
        long total = 0;
        for (int i = 0; i < days; i++) {
            LocalDate date = start.plusDays(i);
            long count = byDate.getOrDefault(date, 0L);
            total += count;
            series.add(ContactGrowth.DailyPoint.builder().date(date).count(count).build());
        }

        List<ContactGrowth.LifecycleSlice> lifecycle = new ArrayList<>();
        for (Object[] row : contactRepository.lifecycleDistribution(workspaceId)) {
            String stage = row[0] == null ? "UNKNOWN" : row[0].toString();
            lifecycle.add(ContactGrowth.LifecycleSlice.builder()
                    .stage(stage).count(asLong(row[1])).build());
        }

        return ContactGrowth.builder()
                .windowDays(days)
                .totalAdded(total)
                .series(series)
                .byLifecycle(lifecycle)
                .build();
    }

    // ── Mailbox health ──

    @Transactional(readOnly = true)
    public MailboxHealth getMailboxHealth() {
        UUID workspaceId = TenantContext.getWorkspaceId();
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        Map<UUID, long[]> byMailbox = new HashMap<>();
        for (Object[] row : emailSendLogRepository.aggregateByMailbox(workspaceId, since)) {
            UUID mailboxId = (UUID) row[0];
            byMailbox.put(mailboxId, new long[] {
                    asLong(row[1]), asLong(row[2]), asLong(row[3])
            });
        }

        List<MailboxHealth.Mailbox> dtos = new ArrayList<>();
        for (Mailbox mb : mailboxRepository.findByWorkspaceId(workspaceId)) {
            long[] counts = byMailbox.getOrDefault(mb.getId(), new long[3]);
            long sent = counts[0];
            long failed = counts[1];
            long bounced = counts[2];

            double bounceRate = (sent + bounced) == 0 ? 0.0
                    : (double) bounced / (double) (sent + bounced);

            dtos.add(MailboxHealth.Mailbox.builder()
                    .mailboxId(mb.getId())
                    .email(mb.getEmail())
                    .name(mb.getName())
                    .status(mb.getStatus().name())
                    .dailyLimit(mb.getDailyLimit())
                    .sendsToday(mb.getSendsToday())
                    .sent(sent)
                    .failed(failed)
                    .bounced(bounced)
                    .bounceRate(bounceRate)
                    .build());
        }

        return MailboxHealth.builder()
                .windowDays(30)
                .mailboxes(dtos)
                .build();
    }

    // ── Helpers ──

    private double rate(long numerator, long denominator) {
        if (denominator == 0) return 0.0;
        return (double) numerator / (double) denominator;
    }

    private static long asLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return 0L;
    }
}
