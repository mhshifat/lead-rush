package com.leadrush.enrichment.job;

import com.leadrush.enrichment.repository.CompanyCrawlRepository;
import com.leadrush.enrichment.service.CompanyCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Scheduled ENQUEUER.
// Every 30 minutes: find (workspace, domain) pairs that have contacts but no crawl row,
// and insert PENDING crawls for them. The worker picks them up on the next tick.
// Capped at 200 new enqueues per sweep to avoid flooding the worker after a big import.
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyCrawlSweepJob {

    private static final int MAX_ENQUEUES_PER_SWEEP = 200;

    private final CompanyCrawlRepository crawlRepository;
    private final CompanyCrawlService crawlService;

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 60_000)
    public void sweep() {
        List<CompanyCrawlRepository.WorkspaceDomain> missing;
        try {
            missing = crawlRepository.findCompaniesMissingCrawl(MAX_ENQUEUES_PER_SWEEP);
        } catch (Exception e) {
            log.warn("Crawl sweep query failed: {}", e.getMessage());
            return;
        }
        if (missing.isEmpty()) return;

        int enqueued = 0;
        for (var wd : missing) {
            try {
                crawlService.enqueueIfMissing(wd.getWorkspaceId(), wd.getDomain());
                enqueued++;
            } catch (Exception e) {
                log.warn("Failed to enqueue crawl for {} / {}: {}",
                        wd.getWorkspaceId(), wd.getDomain(), e.getMessage());
            }
        }
        log.info("Crawl sweep enqueued {} new domain(s)", enqueued);
    }
}
