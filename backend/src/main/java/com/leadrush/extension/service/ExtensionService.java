package com.leadrush.extension.service;

import com.leadrush.ai.service.AIEmailWriterService;
import com.leadrush.auth.entity.User;
import com.leadrush.auth.repository.UserRepository;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.company.entity.Company;
import com.leadrush.company.repository.CompanyRepository;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import com.leadrush.contact.entity.ContactSource;
import com.leadrush.contact.entity.LifecycleStage;
import com.leadrush.contact.repository.ContactEmailRepository;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.email.entity.Mailbox;
import com.leadrush.email.entity.MailboxStatus;
import com.leadrush.email.repository.MailboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.leadrush.extension.dto.*;
import com.leadrush.extension.entity.SavedSearch;
import com.leadrush.extension.repository.SavedSearchRepository;
import com.leadrush.pipeline.entity.Deal;
import com.leadrush.pipeline.entity.PipelineStage;
import com.leadrush.pipeline.repository.DealRepository;
import com.leadrush.security.TenantContext;
import com.leadrush.sequence.entity.EnrollmentStatus;
import com.leadrush.sequence.entity.Sequence;
import com.leadrush.sequence.entity.SequenceEnrollment;
import com.leadrush.sequence.entity.SequenceStatus;
import com.leadrush.sequence.repository.SequenceEnrollmentRepository;
import com.leadrush.sequence.repository.SequenceRepository;
import com.leadrush.sequence.service.EnrollmentService;
import com.leadrush.task.entity.Task;
import com.leadrush.task.repository.TaskRepository;
import com.leadrush.workspace.entity.Workspace;
import com.leadrush.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin service for the /api/v1/ext/* endpoints.
 *
 * Kept separate from TaskService/ContactService because:
 *   - the extension has a narrower surface (only LinkedIn tasks, only dedupe-on-linkedin-url)
 *   - response shapes are intentionally minimal to save bandwidth on a mobile-tier LinkedIn tab
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExtensionService {

    private static final List<Task.TaskType> LINKEDIN_TASK_TYPES =
            List.of(Task.TaskType.LINKEDIN_CONNECT, Task.TaskType.LINKEDIN_MESSAGE);

    private final TaskRepository taskRepository;
    private final ContactRepository contactRepository;
    private final ContactEmailRepository contactEmailRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SequenceEnrollmentRepository enrollmentRepository;
    private final SequenceRepository sequenceRepository;
    private final EnrollmentService enrollmentService;
    private final MailboxRepository mailboxRepository;
    private final AIEmailWriterService aiEmailWriterService;
    private final SavedSearchRepository savedSearchRepository;
    private final DealRepository dealRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${leadrush.frontend-url:http://localhost:4000}")
    private String frontendUrl;

    // ── Connection info for the popup ──

    @Transactional(readOnly = true)
    public MeResponse getMe() {
        UUID userId = TenantContext.getUserId();
        UUID wsId = TenantContext.getWorkspaceId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Workspace ws = workspaceRepository.findById(wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", wsId));

        return MeResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .workspaceId(ws.getId())
                .workspaceName(ws.getName())
                .build();
    }

    // ── Task listing ──

    /** All PENDING LinkedIn tasks for the current workspace. Used by the popup task list. */
    @Transactional(readOnly = true)
    public List<ExtensionTaskResponse> listPendingLinkedInTasks() {
        UUID wsId = TenantContext.getWorkspaceId();
        return taskRepository
                .findByWorkspaceIdAndTaskTypeInAndStatusOrderByDueAtAsc(
                        wsId, LINKEDIN_TASK_TYPES, Task.TaskStatus.PENDING)
                .stream().map(this::toExtensionTask).toList();
    }

    /** Pending LinkedIn tasks for the contact currently being viewed. */
    @Transactional(readOnly = true)
    public List<ExtensionTaskResponse> listPendingTasksForLinkedInUrl(String linkedinUrl) {
        if (linkedinUrl == null || linkedinUrl.isBlank()) return List.of();
        UUID wsId = TenantContext.getWorkspaceId();

        Optional<Contact> contact = contactRepository
                .findFirstByWorkspaceIdAndLinkedinUrlIgnoreCase(wsId, linkedinUrl);
        if (contact.isEmpty()) return List.of();

        return taskRepository.findByWorkspaceIdAndContactIdAndTaskTypeInAndStatus(
                        wsId, contact.get().getId(), LINKEDIN_TASK_TYPES, Task.TaskStatus.PENDING)
                .stream().map(this::toExtensionTask).toList();
    }

    // ── Contact lookup ──

    /**
     * Returns a summary of the contact matching this LinkedIn URL, or null if
     * we haven't imported them yet. The side panel uses this to switch between
     * "Already in Lead Rush" and "Import to Lead Rush" modes on mount.
     */
    @Transactional(readOnly = true)
    public ContactLookupResponse lookupByLinkedInUrl(String linkedinUrl) {
        if (linkedinUrl == null || linkedinUrl.isBlank()) return null;
        UUID wsId = TenantContext.getWorkspaceId();

        return contactRepository
                .findFirstByWorkspaceIdAndLinkedinUrlIgnoreCase(wsId, linkedinUrl.trim())
                .map(c -> buildContactLookup(wsId, c))
                .orElse(null);
    }

    /**
     * Gmail sidebar entry point — resolves the open-thread's sender (or any
     * participant) to a Lead Rush contact. Returns null if we haven't seen
     * this email address, which lets the panel show "Add to Lead Rush"
     * instead of an "already-in" card.
     */
    @Transactional(readOnly = true)
    public ContactLookupResponse lookupByEmail(String email) {
        if (email == null || email.isBlank()) return null;
        UUID wsId = TenantContext.getWorkspaceId();

        return contactEmailRepository
                .findFirstByWorkspaceIdAndEmailIgnoreCase(wsId, email.trim())
                .map(ContactEmail::getContact)
                .map(c -> buildContactLookup(wsId, c))
                .orElse(null);
    }

    private ContactLookupResponse buildContactLookup(UUID wsId, Contact contact) {
        List<String> activeSeqs = enrollmentRepository.findByContactId(contact.getId())
                .stream()
                .filter(e -> e.getStatus() == EnrollmentStatus.ACTIVE)
                .map(SequenceEnrollment::getSequence)
                .filter(s -> s != null)
                .map(s -> s.getName())
                .toList();

        return ContactLookupResponse.builder()
                .contactId(contact.getId())
                .fullName(contact.getFullName())
                .title(contact.getTitle())
                .companyName(contact.getCompany() != null ? contact.getCompany().getName() : null)
                .leadScore(contact.getLeadScore())
                .lifecycleStage(contact.getLifecycleStage() != null
                        ? contact.getLifecycleStage().name() : null)
                .avatarUrl(contact.getAvatarUrl())
                .activeSequenceNames(activeSeqs)
                .lastActivityAt(contact.getUpdatedAt())
                .createdAt(contact.getCreatedAt())
                .contactUrl(stripTrailingSlash(frontendUrl) + "/contacts/" + contact.getId())
                .collisions(recentTeammateCollisions(wsId, contact.getId()))
                .deals(openDealsForContact(wsId, contact.getId()))
                .recentJobChanges(readRecentJobChanges(contact))
                .build();
    }

    /**
     * Return the contact's open deals (OPEN stage type) sorted by expected close
     * date ascending — the panel shows the soonest-closing deal first, which is
     * what a rep needs to know before making contact.
     */
    private List<DealSummary> openDealsForContact(UUID wsId, UUID contactId) {
        List<Deal> deals = dealRepository.findByWorkspaceIdAndContactsId(wsId, contactId);
        if (deals.isEmpty()) return List.of();

        return deals.stream()
                .filter(d -> d.getStage() != null
                        && d.getStage().getStageType() == PipelineStage.StageType.OPEN)
                .sorted((a, b) -> {
                    LocalDate ac = a.getExpectedCloseAt();
                    LocalDate bc = b.getExpectedCloseAt();
                    if (ac == null && bc == null) return 0;
                    if (ac == null) return 1;      // null close dates go last
                    if (bc == null) return -1;
                    return ac.compareTo(bc);
                })
                .map(d -> DealSummary.builder()
                        .dealId(d.getId())
                        .name(d.getName())
                        .stageName(d.getStage().getName())
                        .stageColor(d.getStage().getColor())
                        .stageType(d.getStage().getStageType().name())
                        .winProbability(d.getStage().getWinProbability())
                        .valueAmount(d.getValueAmount())
                        .valueCurrency(d.getValueCurrency())
                        .expectedCloseAt(d.getExpectedCloseAt())
                        .dealUrl(stripTrailingSlash(frontendUrl) + "/deals/" + d.getId())
                        .build())
                .toList();
    }

    /**
     * Build the collision list for the panel. Limits to 3 most recent entries —
     * more than that and the UI just nags. Dedupes by user: one line per teammate
     * showing their most recent action.
     */
    private List<CollisionWarning> recentTeammateCollisions(UUID workspaceId, UUID contactId) {
        UUID currentUserId = TenantContext.getUserId();
        if (currentUserId == null) return List.of();

        LocalDateTime since = LocalDateTime.now().minusDays(14);
        List<Task> recent = taskRepository.findRecentTeammateTouches(
                workspaceId, contactId, currentUserId, since,
                org.springframework.data.domain.PageRequest.of(0, 20)
        );
        if (recent.isEmpty()) return List.of();

        // Preserve newest-first order while deduping by user (first occurrence wins).
        java.util.Map<UUID, Task> byUser = new java.util.LinkedHashMap<>();
        for (Task t : recent) {
            byUser.putIfAbsent(t.getAssignedToUserId(), t);
            if (byUser.size() >= 3) break;
        }

        return byUser.values().stream()
                .map(t -> CollisionWarning.builder()
                        .userName(userRepository.findById(t.getAssignedToUserId())
                                .map(User::getName)
                                .orElse("Teammate"))
                        .action(t.getTaskType().name())
                        .status(t.getStatus().name())
                        .at(t.getCreatedAt())
                        .build())
                .toList();
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ── Sequence enrollment (from the panel) ──

    /**
     * Active sequences the extension can enroll a contact into.
     *
     * {@code hasDefaultMailbox} now reports "can this enroll succeed?" —
     * true if the sequence has its own default OR if the workspace has at
     * least one ACTIVE mailbox we can fall back to. Mirrors the resolution
     * order in {@link #enroll(UUID, UUID)}.
     */
    @Transactional(readOnly = true)
    public List<ExtensionSequenceResponse> listActiveSequences() {
        UUID wsId = TenantContext.getWorkspaceId();
        boolean workspaceHasActiveMailbox = mailboxRepository.findByWorkspaceId(wsId).stream()
                .anyMatch(m -> m.getStatus() == MailboxStatus.ACTIVE);

        return sequenceRepository.findByWorkspaceId(wsId).stream()
                .filter(s -> s.getStatus() == SequenceStatus.ACTIVE)
                .map(s -> ExtensionSequenceResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .stepCount(s.getSteps() != null ? s.getSteps().size() : 0)
                        .hasDefaultMailbox(s.getDefaultMailbox() != null || workspaceHasActiveMailbox)
                        .build())
                .toList();
    }

    /**
     * Enroll from the panel. Mailbox resolution order:
     *   1. Sequence's default mailbox (if set) → EnrollmentService resolves it
     *   2. First ACTIVE mailbox in the workspace (picked here and passed in)
     *   3. No mailboxes → EnrollmentService errors with a clear message
     *
     * We auto-fallback so the panel doesn't need a mailbox picker for sequences
     * that haven't got a default wired up yet — users expect "enroll" to just work.
     */
    @Transactional
    public ExtensionEnrollResponse enroll(UUID sequenceId, UUID contactId) {
        UUID wsId = TenantContext.getWorkspaceId();
        Sequence sequence = sequenceRepository.findById(sequenceId)
                .filter(s -> s.getWorkspaceId().equals(wsId))
                .orElseThrow(() -> new ResourceNotFoundException("Sequence", sequenceId));

        UUID fallbackMailboxId = null;
        if (sequence.getDefaultMailbox() == null) {
            fallbackMailboxId = mailboxRepository.findByWorkspaceId(wsId).stream()
                    .filter(m -> m.getStatus() == MailboxStatus.ACTIVE)
                    .map(Mailbox::getId)
                    .findFirst()
                    .orElse(null);
        }

        var created = enrollmentService.enrollContact(sequenceId, contactId, fallbackMailboxId);

        return ExtensionEnrollResponse.builder()
                .enrollmentId(created.getId())
                .sequenceId(sequenceId)
                .sequenceName(sequence.getName())
                .build();
    }

    // ── Notes / quick activity ──

    /**
     * Creates a NOTE task pre-marked as COMPLETED against the given contact.
     * Surfaces on the contact's activity timeline. Used by the LinkedIn panel
     * when the user drops a quick observation ("Said he'd have budget in Q3").
     */
    @Transactional
    public void addNote(UUID contactId, String body) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();

        Contact contact = contactRepository.findByIdAndWorkspaceId(contactId, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));

        Task note = new Task();
        note.setWorkspaceId(wsId);
        note.setContactId(contact.getId());
        note.setAssignedToUserId(userId);
        note.setTaskType(Task.TaskType.NOTE);
        note.setStatus(Task.TaskStatus.COMPLETED);
        note.setTitle("Note");
        note.setDescription(body.trim());
        note.setCompletedAt(LocalDateTime.now());
        taskRepository.save(note);
        log.info("Extension note added to contact {}", contact.getFullName());
    }

    // ── AI opener generation ──

    /**
     * Build a LinkedIn connection note or short cold-email opener from scraped
     * profile data. Stateless — the extension sends what it just scraped, we
     * don't hit the contact DB here (the prospect may not be imported yet).
     */
    public OpenerResponse generateOpener(OpenerRequest request) {
        String text = aiEmailWriterService.generateOpener(
                request.getFirstName(),
                request.getLastName(),
                request.getTitle(),
                request.getCompanyName(),
                request.getLocation(),
                request.getAbout(),
                request.getChannel(),
                request.getValueProp()
        );
        return OpenerResponse.builder()
                .text(text)
                .length(text == null ? 0 : text.length())
                .channel(request.getChannel())
                .build();
    }

    // ── Saved searches ──

    @Transactional(readOnly = true)
    public List<SavedSearchResponse> listSavedSearches() {
        UUID wsId = TenantContext.getWorkspaceId();
        return savedSearchRepository.findByWorkspaceIdOrderByCreatedAtDesc(wsId).stream()
                .map(this::toSavedSearchResponse)
                .toList();
    }

    /**
     * Upsert by URL — if the user already saved this exact search, we merge
     * rather than reject. Intent is "remember this search for me", not
     * "enforce a uniqueness constraint".
     */
    @Transactional
    public SavedSearchResponse saveSearch(SaveSearchRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        String url = request.getUrl().trim();

        SavedSearch existing = savedSearchRepository.findByWorkspaceIdAndUrl(wsId, url)
                .orElse(null);

        if (existing != null) {
            existing.setName(request.getName().trim());
            if (request.getSeenProfileUrls() != null && !request.getSeenProfileUrls().isEmpty()) {
                // Merge — don't clobber prior known list.
                existing.setKnownProfileUrlsRaw(mergeUrls(
                        existing.getKnownProfileUrlsRaw(), request.getSeenProfileUrls()));
            }
            return toSavedSearchResponse(savedSearchRepository.save(existing));
        }

        SavedSearch fresh = SavedSearch.builder()
                .name(request.getName().trim())
                .url(url)
                .knownProfileUrlsRaw(joinUrls(request.getSeenProfileUrls()))
                .createdByUserId(userId)
                .build();
        fresh.setWorkspaceId(wsId);
        return toSavedSearchResponse(savedSearchRepository.save(fresh));
    }

    /**
     * Returns which profiles on the current page are new since the last check,
     * and atomically merges the new URLs into the known-set. Not-saved URLs
     * get a null {@code savedSearchId} so the panel can offer a "save search" CTA.
     */
    @Transactional
    public CheckSearchResponse checkSavedSearch(CheckSearchRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        String url = request.getUrl().trim();
        List<String> current = request.getCurrentProfileUrls() == null
                ? List.of() : request.getCurrentProfileUrls();

        SavedSearch saved = savedSearchRepository.findByWorkspaceIdAndUrl(wsId, url).orElse(null);
        if (saved == null) {
            return CheckSearchResponse.builder()
                    .savedSearchId(null)
                    .newProfileUrls(List.of())
                    .knownProfileCount(0)
                    .build();
        }

        java.util.Set<String> known = new java.util.LinkedHashSet<>(splitUrls(saved.getKnownProfileUrlsRaw()));
        List<String> newly = new java.util.ArrayList<>();
        for (String u : current) {
            if (u == null || u.isBlank()) continue;
            String lower = u.trim();
            if (!known.contains(lower)) {
                newly.add(lower);
                known.add(lower);
            }
        }

        saved.setKnownProfileUrlsRaw(joinUrls(known));
        saved.setLastCheckedAt(LocalDateTime.now());
        savedSearchRepository.save(saved);

        return CheckSearchResponse.builder()
                .savedSearchId(saved.getId())
                .name(saved.getName())
                .newProfileUrls(newly)
                .knownProfileCount(known.size())
                .build();
    }

    @Transactional
    public void deleteSavedSearch(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        SavedSearch saved = savedSearchRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("SavedSearch", id));
        savedSearchRepository.delete(saved);
    }

    // ── Saved-search helpers ──

    private SavedSearchResponse toSavedSearchResponse(SavedSearch s) {
        int count = splitUrls(s.getKnownProfileUrlsRaw()).size();
        return SavedSearchResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .url(s.getUrl())
                .knownProfileCount(count)
                .lastCheckedAt(s.getLastCheckedAt())
                .createdAt(s.getCreatedAt())
                .build();
    }

    /** Newline-delimited because URLs never contain '\n' — cheaper than JSON. */
    private static String joinUrls(java.util.Collection<String> urls) {
        if (urls == null || urls.isEmpty()) return "";
        return String.join("\n", urls);
    }

    private static List<String> splitUrls(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return java.util.Arrays.stream(raw.split("\n"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** Union preserving insertion order (known list first, then any new arrivals). */
    private static String mergeUrls(String existingRaw, List<String> addition) {
        java.util.Set<String> merged = new java.util.LinkedHashSet<>(splitUrls(existingRaw));
        for (String u : addition) {
            if (u != null && !u.isBlank()) merged.add(u.trim());
        }
        return joinUrls(merged);
    }

    // ── Scraper telemetry ──

    /**
     * Records a scraper selector miss. Today: logs it. Tomorrow: aggregate
     * into a `scraper_misses` table and surface in admin ops so we see the
     * spike when LinkedIn ships a DOM change.
     */
    public void recordScraperMiss(ScraperTelemetryRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        log.warn(
                "scraper miss — workspace={} layout={} url={} missedFields={} version={}",
                wsId,
                request.getLayout(),
                request.getUrl(),
                request.getMissedFields(),
                request.getScraperVersion()
        );
    }

    @Transactional
    public void completeTask(UUID taskId) {
        UUID wsId = TenantContext.getWorkspaceId();
        Task task = taskRepository.findById(taskId)
                .filter(t -> t.getWorkspaceId().equals(wsId))
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (task.getStatus() == Task.TaskStatus.COMPLETED) return;

        task.setStatus(Task.TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);
    }

    // ── Duplicate detector ──

    /**
     * Fuzzy-match existing contacts by scraped name before the panel commits to
     * a fresh import. Catches the case where a lead was captured earlier from a
     * different source (website form, CSV import) with no LinkedIn URL attached.
     */
    @Transactional(readOnly = true)
    public List<PossibleMatchResponse> findPossibleMatches(LinkedInImportRequest request) {
        if (!notBlank(request.getFirstName())) return List.of();
        UUID wsId = TenantContext.getWorkspaceId();
        String url = request.getLinkedinUrl() == null ? "" : request.getLinkedinUrl().trim();

        List<Contact> matches = contactRepository.findPossibleMatches(
                wsId,
                request.getFirstName().trim(),
                notBlank(request.getLastName()) ? request.getLastName().trim() : null,
                url,
                org.springframework.data.domain.PageRequest.of(0, 5)
        );

        return matches.stream()
                .map(c -> PossibleMatchResponse.builder()
                        .contactId(c.getId())
                        .fullName(c.getFullName())
                        .title(c.getTitle())
                        .companyName(c.getCompany() != null ? c.getCompany().getName() : null)
                        .avatarUrl(c.getAvatarUrl())
                        .linkedinUrl(c.getLinkedinUrl())
                        .reason(matchReason(c, request))
                        .build())
                .toList();
    }

    private String matchReason(Contact c, LinkedInImportRequest req) {
        String reqCompany = req.getCompanyName();
        String contactCompany = c.getCompany() != null ? c.getCompany().getName() : null;
        if (notBlank(reqCompany) && notBlank(contactCompany)
                && reqCompany.trim().equalsIgnoreCase(contactCompany.trim())) {
            return "NAME_COMPANY";
        }
        return "NAME";
    }

    // ── Profile import ──

    /**
     * Find-or-create a contact based on a LinkedIn URL match. We update fields only when
     * the caller sent a non-blank value, so the extension never wipes out data a user
     * curated in Lead Rush.
     *
     * When {@code mergeIntoContactId} is set on the request, we bypass the
     * linkedinUrl lookup and merge into that specific contact — the duplicate
     * detector flow uses this to attach a newly-discovered LinkedIn URL to a
     * lead imported earlier from another source.
     */
    @Transactional
    public LinkedInImportResponse importFromLinkedIn(LinkedInImportRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        String url = request.getLinkedinUrl().trim();

        Optional<Contact> existing;
        if (request.getMergeIntoContactId() != null) {
            existing = contactRepository.findByIdAndWorkspaceId(request.getMergeIntoContactId(), wsId);
            if (existing.isEmpty()) {
                throw new ResourceNotFoundException("Contact", request.getMergeIntoContactId());
            }
        } else {
            existing = contactRepository.findFirstByWorkspaceIdAndLinkedinUrlIgnoreCase(wsId, url);
        }

        Contact contact;
        boolean created;
        List<JobChangeEvent> detectedChanges = List.of();

        if (existing.isPresent()) {
            contact = existing.get();
            created = false;
            // Snapshot BEFORE applyUpdates mutates the entity — this is what we
            // diff against the incoming scraped values to emit job-change events.
            String prevTitle = contact.getTitle();
            String prevCompany = contact.getCompany() != null ? contact.getCompany().getName() : null;

            // When merging from the duplicate detector, explicitly set the
            // LinkedIn URL — the contact didn't have one before.
            if (request.getMergeIntoContactId() != null
                    && (contact.getLinkedinUrl() == null || contact.getLinkedinUrl().isBlank())) {
                contact.setLinkedinUrl(url);
            }
            applyUpdates(contact, request);

            detectedChanges = detectJobChanges(prevTitle, prevCompany, request);
        } else {
            contact = new Contact();
            contact.setWorkspaceId(wsId);
            contact.setSource(ContactSource.MANUAL);
            contact.setLifecycleStage(LifecycleStage.LEAD);
            contact.setLinkedinUrl(url);
            contact.setFirstName(firstOrFallback(request.getFirstName(), "LinkedIn"));
            contact.setLastName(request.getLastName());
            applyUpdates(contact, request);
            created = true;
        }

        // Company link — find by name (case-insensitive) or create a new one
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()
                && contact.getCompany() == null) {
            contact.setCompany(findOrCreateCompany(wsId, request.getCompanyName().trim()));
        }

        // Persist detected changes into contact.metadata.jobChanges BEFORE save.
        // We append rather than replace so the panel can show the last few
        // changes on revisits without needing a dedicated table.
        if (!detectedChanges.isEmpty()) {
            appendJobChangesToMetadata(contact, detectedChanges);
        }

        contact = contactRepository.save(contact);
        log.info("LinkedIn import: {} (created={}) url={} changes={}",
                contact.getFullName(), created, url, detectedChanges.size());

        LinkedInImportResponse.LinkedInImportResponseBuilder response = LinkedInImportResponse.builder()
                .contactId(contact.getId())
                .fullName(contact.getFullName())
                .created(created)
                .jobChanges(detectedChanges);

        // Post-connect auto-enroll: fired right after the user hits Connect on
        // LinkedIn. We never fail the whole import on an enroll error — the
        // caller already has a saved contact; surfacing enrollError lets the
        // panel toast "Imported — enroll failed: ..." instead of losing the lead.
        if (request.getAutoEnrollSequenceId() != null) {
            try {
                ExtensionEnrollResponse enroll = enroll(request.getAutoEnrollSequenceId(), contact.getId());
                response.enrollmentId(enroll.getEnrollmentId());
                response.enrolledSequenceName(enroll.getSequenceName());
            } catch (Exception e) {
                log.warn("auto-enroll failed after import of {}: {}", contact.getId(), e.getMessage());
                response.enrollError(e.getMessage());
            }
        }

        return response.build();
    }

    // ── Helpers ──

    private void applyUpdates(Contact contact, LinkedInImportRequest request) {
        if (notBlank(request.getFirstName())) contact.setFirstName(request.getFirstName().trim());
        if (notBlank(request.getLastName()))  contact.setLastName(request.getLastName().trim());
        if (notBlank(request.getTitle()))     contact.setTitle(request.getTitle().trim());
        if (notBlank(request.getAvatarUrl())) contact.setAvatarUrl(request.getAvatarUrl().trim());
        applyDeepScrapeMetadata(contact, request);
    }

    /**
     * Merges deep-scrape fields into {@code contact.metadata} under a {@code linkedin}
     * object. We never clobber existing non-linkedin metadata keys, and only overwrite
     * the linkedin sub-fields the scraper actually produced this call.
     */
    /**
     * Compare the scraped request against the pre-update contact state and
     * emit one JobChangeEvent per changed field. Treats blank/null as "unknown" —
     * we never emit a change when the incoming field is empty (scraper missed it).
     */
    private List<JobChangeEvent> detectJobChanges(
            String prevTitle, String prevCompany, LinkedInImportRequest request
    ) {
        List<JobChangeEvent> changes = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        String newTitle = request.getTitle();
        if (notBlank(newTitle) && notBlank(prevTitle)
                && !newTitle.trim().equalsIgnoreCase(prevTitle.trim())) {
            changes.add(JobChangeEvent.builder()
                    .type("TITLE")
                    .from(prevTitle)
                    .to(newTitle.trim())
                    .at(now)
                    .build());
        }

        String newCompany = request.getCompanyName();
        if (notBlank(newCompany) && notBlank(prevCompany)
                && !newCompany.trim().equalsIgnoreCase(prevCompany.trim())) {
            changes.add(JobChangeEvent.builder()
                    .type("COMPANY")
                    .from(prevCompany)
                    .to(newCompany.trim())
                    .at(now)
                    .build());
        }
        return changes;
    }

    /**
     * Append job-change events to {@code contact.metadata.jobChanges}. Capped
     * at 20 entries — older events are dropped to keep the JSON blob bounded.
     */
    private void appendJobChangesToMetadata(Contact contact, List<JobChangeEvent> events) {
        try {
            String current = contact.getMetadata() == null || contact.getMetadata().isBlank()
                    ? "{}" : contact.getMetadata();
            ObjectNode root = (ObjectNode) objectMapper.readTree(current);
            com.fasterxml.jackson.databind.node.ArrayNode existing = root.has("jobChanges")
                    && root.get("jobChanges").isArray()
                    ? (com.fasterxml.jackson.databind.node.ArrayNode) root.get("jobChanges")
                    : root.putArray("jobChanges");

            for (JobChangeEvent e : events) {
                ObjectNode node = existing.addObject();
                node.put("type", e.getType());
                node.put("from", e.getFrom());
                node.put("to", e.getTo());
                node.put("at", e.getAt().toString());
            }
            // Keep only the most recent 20 — older entries fall out.
            while (existing.size() > 20) existing.remove(0);

            root.set("jobChanges", existing);
            contact.setMetadata(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("Failed to append job-change events for contact {}: {}",
                    contact.getId(), e.getMessage());
        }
    }

    /**
     * Read up to the 5 most recent job changes from {@code contact.metadata},
     * newest-first, filtered to the last 90 days. Used by the side-panel
     * contact lookup so returning visits still see the badge.
     */
    private List<JobChangeEvent> readRecentJobChanges(Contact contact) {
        if (contact.getMetadata() == null || contact.getMetadata().isBlank()) return List.of();
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(contact.getMetadata());
            com.fasterxml.jackson.databind.JsonNode arr = root.get("jobChanges");
            if (arr == null || !arr.isArray() || arr.isEmpty()) return List.of();

            LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
            List<JobChangeEvent> events = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                LocalDateTime at;
                try {
                    at = LocalDateTime.parse(n.path("at").asText());
                } catch (Exception ignored) { continue; }
                if (at.isBefore(cutoff)) continue;
                events.add(JobChangeEvent.builder()
                        .type(n.path("type").asText(null))
                        .from(n.path("from").asText(null))
                        .to(n.path("to").asText(null))
                        .at(at)
                        .build());
            }
            // newest-first, cap at 5 — the panel doesn't need a full history.
            events.sort((a, b) -> b.getAt().compareTo(a.getAt()));
            return events.size() > 5 ? events.subList(0, 5) : events;
        } catch (Exception e) {
            log.warn("Failed to read job changes for contact {}: {}", contact.getId(), e.getMessage());
            return List.of();
        }
    }

    private void applyDeepScrapeMetadata(Contact contact, LinkedInImportRequest request) {
        boolean hasAnyDeepField =
                notBlank(request.getAbout())
                || (request.getExperiences() != null && !request.getExperiences().isEmpty())
                || (request.getEducation() != null && !request.getEducation().isEmpty())
                || (request.getSkills() != null && !request.getSkills().isEmpty());
        if (!hasAnyDeepField) return;

        try {
            String current = contact.getMetadata() == null || contact.getMetadata().isBlank()
                    ? "{}" : contact.getMetadata();
            ObjectNode root = (ObjectNode) objectMapper.readTree(current);
            ObjectNode linkedin = root.has("linkedin") && root.get("linkedin").isObject()
                    ? (ObjectNode) root.get("linkedin")
                    : root.putObject("linkedin");

            if (notBlank(request.getAbout())) {
                linkedin.put("about", request.getAbout().trim());
            }
            if (request.getExperiences() != null && !request.getExperiences().isEmpty()) {
                linkedin.set("experiences", objectMapper.valueToTree(request.getExperiences()));
            }
            if (request.getEducation() != null && !request.getEducation().isEmpty()) {
                linkedin.set("education", objectMapper.valueToTree(request.getEducation()));
            }
            if (request.getSkills() != null && !request.getSkills().isEmpty()) {
                linkedin.set("skills", objectMapper.valueToTree(request.getSkills()));
            }
            linkedin.put("scrapedAt", LocalDateTime.now().toString());

            contact.setMetadata(objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            // Never let a scrape-metadata issue block the import — the essential
            // fields (name, title, company) are already applied above.
            log.warn("Failed to merge LinkedIn scrape metadata: {}", e.getMessage());
        }
    }

    private Company findOrCreateCompany(UUID workspaceId, String name) {
        return companyRepository.findByWorkspaceId(workspaceId,
                        org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> {
                    Company c = Company.builder().name(name).build();
                    c.setWorkspaceId(workspaceId);
                    return companyRepository.save(c);
                });
    }

    private ExtensionTaskResponse toExtensionTask(Task task) {
        Contact contact = task.getContactId() != null
                ? contactRepository.findById(task.getContactId()).orElse(null)
                : null;

        return ExtensionTaskResponse.builder()
                .id(task.getId())
                .type(task.getTaskType().name())
                .title(task.getTitle())
                .description(task.getDescription())
                .contactId(task.getContactId())
                .contactName(contact != null ? contact.getFullName() : null)
                .contactTitle(contact != null ? contact.getTitle() : null)
                .contactCompany(contact != null && contact.getCompany() != null
                        ? contact.getCompany().getName() : null)
                .contactLinkedinUrl(contact != null ? contact.getLinkedinUrl() : null)
                .dueAt(task.getDueAt())
                .build();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstOrFallback(String value, String fallback) {
        return notBlank(value) ? value.trim() : fallback;
    }
}
