package com.leadrush.extension.service;

import com.leadrush.auth.entity.User;
import com.leadrush.auth.repository.UserRepository;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.company.entity.Company;
import com.leadrush.company.repository.CompanyRepository;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactSource;
import com.leadrush.contact.entity.LifecycleStage;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.extension.dto.*;
import com.leadrush.security.TenantContext;
import com.leadrush.task.entity.Task;
import com.leadrush.task.repository.TaskRepository;
import com.leadrush.workspace.entity.Workspace;
import com.leadrush.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

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

    // ── Profile import ──

    /**
     * Find-or-create a contact based on a LinkedIn URL match. We update fields only when
     * the caller sent a non-blank value, so the extension never wipes out data a user
     * curated in Lead Rush.
     */
    @Transactional
    public LinkedInImportResponse importFromLinkedIn(LinkedInImportRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        String url = request.getLinkedinUrl().trim();

        Optional<Contact> existing = contactRepository
                .findFirstByWorkspaceIdAndLinkedinUrlIgnoreCase(wsId, url);

        Contact contact;
        boolean created;

        if (existing.isPresent()) {
            contact = existing.get();
            created = false;
            applyUpdates(contact, request);
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

        contact = contactRepository.save(contact);
        log.info("LinkedIn import: {} (created={}) url={}", contact.getFullName(), created, url);

        return LinkedInImportResponse.builder()
                .contactId(contact.getId())
                .fullName(contact.getFullName())
                .created(created)
                .build();
    }

    // ── Helpers ──

    private void applyUpdates(Contact contact, LinkedInImportRequest request) {
        if (notBlank(request.getFirstName())) contact.setFirstName(request.getFirstName().trim());
        if (notBlank(request.getLastName()))  contact.setLastName(request.getLastName().trim());
        if (notBlank(request.getTitle()))     contact.setTitle(request.getTitle().trim());
        if (notBlank(request.getAvatarUrl())) contact.setAvatarUrl(request.getAvatarUrl().trim());
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
