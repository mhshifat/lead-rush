package com.leadrush.email.service;

import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.entity.MailboxStatus;
import com.leadrush.email.repository.MailboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Round-robin mailbox picker. In-memory counter, resets on process restart. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailboxRotationService {

    private final MailboxRepository mailboxRepository;

    private final ConcurrentHashMap<UUID, AtomicInteger> counters = new ConcurrentHashMap<>();

    public Optional<Mailbox> pickMailboxForWorkspace(UUID workspaceId) {
        List<Mailbox> candidates = mailboxRepository.findByWorkspaceId(workspaceId).stream()
                .filter(mb -> mb.getStatus() == MailboxStatus.ACTIVE)
                .filter(Mailbox::canSendToday)
                .toList();

        if (candidates.isEmpty()) {
            log.warn("No available mailboxes in workspace {}", workspaceId);
            return Optional.empty();
        }

        AtomicInteger counter = counters.computeIfAbsent(workspaceId, k -> new AtomicInteger(0));
        int index = Math.floorMod(counter.getAndIncrement(), candidates.size());
        return Optional.of(candidates.get(index));
    }

    public void resetRotation(UUID workspaceId) {
        counters.remove(workspaceId);
    }
}
