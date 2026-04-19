package com.leadrush.enrichment.job;

import com.leadrush.enrichment.entity.CompanyCrawl;
import com.leadrush.enrichment.repository.CompanyCrawlRepository;
import com.leadrush.enrichment.service.CompanyCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Scheduled WORKER.
// Every 60 seconds: pick up to BATCH_SIZE due crawl rows and process each.
// Each crawl runs in its own transaction (see CompanyCrawlService.processCrawl) so one
// failure doesn't taint the rest of the batch.
//
// Crawls are bandwidth-heavy (sitemap fetch + multiple HTML pages + polite delays) —
// keeping the batch small prevents any single tick from blowing the thread budget.
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyCrawlWorkerJob {

    private static final int BATCH_SIZE = 3;

    private final CompanyCrawlRepository crawlRepository;
    private final CompanyCrawlService crawlService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void runBatch() {
        List<CompanyCrawl> due;
        try {
            due = crawlRepository.findDue(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        } catch (Exception e) {
            log.warn("Crawl worker query failed: {}", e.getMessage());
            return;
        }
        if (due.isEmpty()) return;

        log.debug("Processing {} due crawl(s)", due.size());
        for (CompanyCrawl crawl : due) {
            try {
                crawlService.processCrawl(crawl.getId());
            } catch (Exception e) {
                log.warn("Crawl {} threw: {}", crawl.getId(), e.getMessage());
            }
        }
    }
}
