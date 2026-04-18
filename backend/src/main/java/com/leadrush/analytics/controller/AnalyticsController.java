package com.leadrush.analytics.controller;

import com.leadrush.analytics.dto.*;
import com.leadrush.analytics.service.AnalyticsService;
import com.leadrush.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /** Top-of-dashboard summary metrics. */
    @GetMapping("/overview")
    public ApiResponse<DashboardOverview> getOverview() {
        return ApiResponse.success(analyticsService.getOverview());
    }

    /** Per-sequence rates (open/click/reply) — used on the dashboard table. */
    @GetMapping("/sequences")
    public ApiResponse<List<SequenceAnalytics>> getSequencePerformance() {
        return ApiResponse.success(analyticsService.getSequencePerformance());
    }

    /** Step-by-step drop-off funnel for a specific sequence. */
    @GetMapping("/sequences/{id}/funnel")
    public ApiResponse<SequenceFunnel> getSequenceFunnel(@PathVariable UUID id) {
        return ApiResponse.success(analyticsService.getSequenceFunnel(id));
    }

    /** All pipelines with per-stage deal counts + values. */
    @GetMapping("/pipelines")
    public ApiResponse<List<PipelineReport>> getPipelineReports() {
        return ApiResponse.success(analyticsService.getPipelineReports());
    }

    /** Daily new-contact counts for the last `days` (default 30, max 365). */
    @GetMapping("/contact-growth")
    public ApiResponse<ContactGrowth> getContactGrowth(
            @RequestParam(defaultValue = "30") int days
    ) {
        return ApiResponse.success(analyticsService.getContactGrowth(days));
    }

    /** Per-mailbox send volume + bounce rate for the last 30 days. */
    @GetMapping("/mailboxes")
    public ApiResponse<MailboxHealth> getMailboxHealth() {
        return ApiResponse.success(analyticsService.getMailboxHealth());
    }
}
