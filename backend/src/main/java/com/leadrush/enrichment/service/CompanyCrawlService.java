package com.leadrush.enrichment.service;

import com.leadrush.enrichment.adapter.impl.SitemapCrawlerAdapter;
import com.leadrush.enrichment.entity.CompanyCrawl;
import com.leadrush.enrichment.entity.CompanyCrawlPerson;
import com.leadrush.enrichment.repository.CompanyCrawlPersonRepository;
import com.leadrush.enrichment.repository.CompanyCrawlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Orchestrates background domain crawls.
//
//   enqueueIfMissing()   → called by the sweep job to queue new (workspace, domain) pairs
//   processCrawl(id)     → called by the worker job to execute a single queued crawl
//
// Keeping the "sweep" and "worker" as separate responsibilities (two @Scheduled jobs) means
// a slow crawl doesn't block enqueuing, and a flood of new domains doesn't back up the worker.
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyCrawlService {

    // Crawl fails after this many attempts and parks in FAILED status until manually retried.
    private static final int MAX_ATTEMPTS = 3;

    // Retry backoff on failure: 30min, 2h, 12h.
    private static final long[] RETRY_DELAYS_MINUTES = { 30, 120, 720 };

    private final CompanyCrawlRepository crawlRepository;
    private final CompanyCrawlPersonRepository personRepository;
    private final SitemapCrawlerAdapter crawler;

    @Transactional
    public void enqueueIfMissing(UUID workspaceId, String domain) {
        if (workspaceId == null || domain == null || domain.isBlank()) return;
        String normalised = domain.toLowerCase().trim();

        if (crawlRepository.findByWorkspaceIdAndDomain(workspaceId, normalised).isPresent()) return;

        CompanyCrawl crawl = CompanyCrawl.builder()
                .domain(normalised)
                .status(CompanyCrawl.Status.PENDING)
                .nextAttemptAt(LocalDateTime.now())
                .build();
        crawl.setWorkspaceId(workspaceId);
        crawlRepository.save(crawl);
        log.debug("Enqueued crawl: workspace={} domain={}", workspaceId, normalised);
    }

    // Runs in its OWN transaction so an exception on one crawl doesn't roll back siblings
    // the worker processes in the same batch.
    @Transactional
    public void processCrawl(UUID crawlId) {
        CompanyCrawl crawl = crawlRepository.findById(crawlId).orElse(null);
        if (crawl == null) return;
        if (crawl.getStatus() == CompanyCrawl.Status.IN_PROGRESS) return;   // someone else is running it
        if (crawl.getStatus() == CompanyCrawl.Status.COMPLETED) return;

        crawl.setStatus(CompanyCrawl.Status.IN_PROGRESS);
        crawl.setAttemptCount(crawl.getAttemptCount() + 1);
        crawl.setLastAttemptAt(LocalDateTime.now());
        crawlRepository.save(crawl);

        List<SitemapCrawlerAdapter.DiscoveredPerson> discovered;
        try {
            discovered = crawler.crawlDomain(crawl.getDomain());
        } catch (Exception e) {
            markFailed(crawl, e.getMessage());
            return;
        }

        // Replace any previously-stored people at the same (workspace, domain) — latest crawl wins.
        personRepository.deleteByWorkspaceIdAndDomain(crawl.getWorkspaceId(), crawl.getDomain());

        int saved = 0;
        for (SitemapCrawlerAdapter.DiscoveredPerson p : discovered) {
            if (p.email() == null && p.name() == null) continue;
            CompanyCrawlPerson row = CompanyCrawlPerson.builder()
                    .domain(crawl.getDomain())
                    .name(p.name())
                    .email(p.email() == null ? null : p.email().toLowerCase())
                    .jobTitle(p.title())
                    .sourceUrl(p.sourceUrl())
                    .sourceAdapter("SITEMAP_CRAWLER")
                    .discoveredAt(LocalDateTime.now())
                    .build();
            row.setWorkspaceId(crawl.getWorkspaceId());
            personRepository.save(row);
            saved++;
        }

        crawl.setStatus(CompanyCrawl.Status.COMPLETED);
        crawl.setPersonsFound(saved);
        crawl.setLastCrawledAt(LocalDateTime.now());
        crawl.setLastError(null);
        crawlRepository.save(crawl);
        log.info("Crawl COMPLETED: {} ({} persons found)", crawl.getDomain(), saved);
    }

    private void markFailed(CompanyCrawl crawl, String error) {
        if (crawl.getAttemptCount() >= MAX_ATTEMPTS) {
            crawl.setStatus(CompanyCrawl.Status.FAILED);
            // Don't schedule further retries — park in FAILED.
        } else {
            int idx = Math.min(crawl.getAttemptCount() - 1, RETRY_DELAYS_MINUTES.length - 1);
            crawl.setStatus(CompanyCrawl.Status.FAILED);
            crawl.setNextAttemptAt(LocalDateTime.now().plusMinutes(RETRY_DELAYS_MINUTES[idx]));
        }
        crawl.setLastError(error == null ? "unknown" : truncate(error));
        crawlRepository.save(crawl);
        log.warn("Crawl FAILED: {} ({}/{}): {}",
                crawl.getDomain(), crawl.getAttemptCount(), MAX_ATTEMPTS, error);
    }

    private static String truncate(String s) { return s.length() > 1000 ? s.substring(0, 1000) : s; }
}
