package com.leadrush.enrichment.service;

import com.leadrush.enrichment.entity.DomainEmailPattern;
import com.leadrush.enrichment.entity.EmailPatternType;
import com.leadrush.enrichment.repository.DomainEmailPatternRepository;
import com.leadrush.enrichment.util.EmailPatternGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// Cache of "at domain X, emails follow pattern Y" mappings.
// Every enrichment SUCCESS that produced an email is inferred into a pattern entry here,
// so the next person at the same domain gets a cache hit and zero external calls.
@Service
@RequiredArgsConstructor
@Slf4j
public class DomainPatternCacheService {

    private final DomainEmailPatternRepository repository;

    @Transactional(readOnly = true)
    public Optional<DomainEmailPattern> lookup(UUID workspaceId, String domain) {
        if (workspaceId == null || domain == null || domain.isBlank()) return Optional.empty();
        return repository.findByWorkspaceIdAndDomain(workspaceId, domain.toLowerCase());
    }

    // Construct the expected email for this (first, last, domain) if we have a cached pattern
    // for that domain. Returns null when no pattern is known or the required name parts aren't available.
    @Transactional(readOnly = true)
    public String constructEmail(UUID workspaceId, String firstName, String lastName, String domain) {
        return lookup(workspaceId, domain)
                .map(p -> EmailPatternGenerator.applyPattern(p.getPatternType(), firstName, lastName, domain))
                .orElse(null);
    }

    // Called by the waterfall after any adapter returns SUCCESS + an email. Infers which
    // pattern produced the email and saves/upgrades the cache entry.
    @Transactional
    public void recordDiscoveredEmail(
            UUID workspaceId, String firstName, String lastName,
            String domain, String foundEmail, String source
    ) {
        if (workspaceId == null || foundEmail == null || domain == null || domain.isBlank()) return;

        EmailPatternType inferred = inferPattern(firstName, lastName, domain, foundEmail);
        if (inferred == null) {
            // Email doesn't match any known pattern (e.g. ringo@beatles.com where first="Richard")
            // — not worth caching because we can't generalize it to future contacts.
            return;
        }

        DomainEmailPattern existing = repository
                .findByWorkspaceIdAndDomain(workspaceId, domain.toLowerCase())
                .orElse(null);

        if (existing == null) {
            DomainEmailPattern fresh = DomainEmailPattern.builder()
                    .domain(domain.toLowerCase())
                    .patternType(inferred)
                    .confidence(1)
                    .source(source)
                    .lastConfirmedAt(LocalDateTime.now())
                    .build();
            fresh.setWorkspaceId(workspaceId);
            repository.save(fresh);
            log.info("Learned email pattern {} for {} (source={})", inferred, domain, source);
            return;
        }

        // Same pattern confirmed again → bump confidence. Different pattern at the same
        // domain is either a misconfiguration or the company has multiple formats — keep
        // the one with higher confidence, don't flip flop.
        if (existing.getPatternType() == inferred) {
            existing.setConfidence(existing.getConfidence() + 1);
            existing.setLastConfirmedAt(LocalDateTime.now());
            repository.save(existing);
        } else if (existing.getConfidence() <= 1) {
            existing.setPatternType(inferred);
            existing.setSource(source);
            existing.setLastConfirmedAt(LocalDateTime.now());
            repository.save(existing);
        }
    }

    @Transactional
    public void markCatchAll(UUID workspaceId, String domain) {
        if (workspaceId == null || domain == null || domain.isBlank()) return;
        DomainEmailPattern existing = repository
                .findByWorkspaceIdAndDomain(workspaceId, domain.toLowerCase())
                .orElse(null);
        if (existing != null && !existing.isCatchAll()) {
            existing.setCatchAll(true);
            repository.save(existing);
        }
    }

    // Brute-force infer: generate every candidate pattern for (first, last, domain) and
    // return the one that matches the email the adapter produced.
    private EmailPatternType inferPattern(String first, String last, String domain, String email) {
        String needle = email.trim().toLowerCase();
        return EmailPatternGenerator.generate(first, last, domain).entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(needle))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
