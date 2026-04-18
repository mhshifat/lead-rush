package com.leadrush.chat.service;

import com.leadrush.auth.entity.User;
import com.leadrush.auth.repository.UserRepository;
import com.leadrush.chat.dto.*;
import com.leadrush.chat.entity.ChatConversation;
import com.leadrush.chat.entity.ChatMessage;
import com.leadrush.chat.entity.ChatWidget;
import com.leadrush.chat.repository.ChatConversationRepository;
import com.leadrush.chat.repository.ChatMessageRepository;
import com.leadrush.chat.repository.ChatWidgetRepository;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.entity.ContactEmail;
import com.leadrush.contact.entity.ContactSource;
import com.leadrush.contact.entity.LifecycleStage;
import com.leadrush.contact.repository.ContactEmailRepository;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.notification.entity.NotificationType;
import com.leadrush.notification.service.NotificationService;
import com.leadrush.security.TenantContext;
import com.leadrush.webhook.entity.WebhookEventType;
import com.leadrush.webhook.service.WebhookService;
import com.leadrush.workspace.entity.Workspace;
import com.leadrush.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat backend serving both public visitors (visitorToken-identified) and agents (JWT).
 * Messages persist first, then broadcast via STOMP to
 * /topic/chat/visitor/{token} and /topic/chat/workspace/{id}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatWidgetRepository widgetRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ContactRepository contactRepository;
    private final ContactEmailRepository contactEmailRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final WebhookService webhookService;

    // ── PUBLIC: widget config ──

    @Transactional
    public ChatWidgetConfig getPublicConfig(String workspaceSlug) {
        Workspace workspace = workspaceRepository.findBySlug(workspaceSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceSlug));
        ChatWidget widget = getOrCreateWidget(workspace.getId());
        return ChatWidgetConfig.builder()
                .workspaceSlug(workspace.getSlug())
                .workspaceName(workspace.getName())
                .enabled(widget.isEnabled())
                .displayName(widget.getDisplayName())
                .greeting(widget.getGreeting())
                .offlineMessage(widget.getOfflineMessage())
                .primaryColor(widget.getPrimaryColor())
                .position(widget.getPosition())
                .requireEmail(widget.isRequireEmail())
                .build();
    }

    // ── PUBLIC: visitor starts / continues a conversation ──

    @Transactional
    public PublicConversationResponse start(PublicStartConversationRequest request) {
        Workspace workspace = workspaceRepository.findBySlug(request.getWorkspaceSlug())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", request.getWorkspaceSlug()));
        ChatWidget widget = getOrCreateWidget(workspace.getId());
        if (!widget.isEnabled()) {
            throw new BusinessException("Chat is currently offline");
        }

        // Resume existing conversation if visitor already had one
        ChatConversation conversation = null;
        if (request.getVisitorToken() != null && !request.getVisitorToken().isBlank()) {
            conversation = conversationRepository
                    .findByVisitorToken(request.getVisitorToken())
                    .filter(c -> c.getWorkspaceId().equals(workspace.getId()))
                    .filter(c -> c.getStatus() == ChatConversation.Status.OPEN)
                    .orElse(null);
        }

        if (conversation == null) {
            conversation = ChatConversation.builder()
                    .visitorToken(UUID.randomUUID().toString())
                    .visitorName(request.getVisitorName())
                    .visitorEmail(request.getVisitorEmail())
                    .status(ChatConversation.Status.OPEN)
                    .sourceUrl(request.getSourceUrl())
                    .userAgent(truncate(request.getUserAgent(), 500))
                    .build();
            conversation.setWorkspaceId(workspace.getId());
            conversation = conversationRepository.save(conversation);

            // Seed system greeting as the first message
            persistMessage(conversation, ChatMessage.Sender.SYSTEM, null, widget.getGreeting());
        } else {
            // Update identity if the visitor gave new info
            boolean changed = false;
            if (notBlank(request.getVisitorName()) && !Objects.equals(request.getVisitorName(), conversation.getVisitorName())) {
                conversation.setVisitorName(request.getVisitorName());
                changed = true;
            }
            if (notBlank(request.getVisitorEmail()) && !Objects.equals(request.getVisitorEmail(), conversation.getVisitorEmail())) {
                conversation.setVisitorEmail(request.getVisitorEmail());
                changed = true;
            }
            if (changed) conversationRepository.save(conversation);
        }

        // Link or create a Contact once we have an email
        linkContact(conversation);

        // Persist the visitor's message + broadcast + notify + webhook
        appendVisitorMessage(conversation, request.getMessage());

        return PublicConversationResponse.builder()
                .conversationId(conversation.getId())
                .visitorToken(conversation.getVisitorToken())
                .messages(loadMessageResponses(conversation.getId()))
                .build();
    }

    @Transactional
    public PublicConversationResponse visitorSend(PublicSendMessageRequest request) {
        ChatConversation conversation = conversationRepository
                .findByVisitorToken(request.getVisitorToken())
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", request.getVisitorToken()));
        if (conversation.getStatus() == ChatConversation.Status.CLOSED) {
            throw new BusinessException("This conversation is closed");
        }
        appendVisitorMessage(conversation, request.getMessage());
        return PublicConversationResponse.builder()
                .conversationId(conversation.getId())
                .visitorToken(conversation.getVisitorToken())
                .messages(loadMessageResponses(conversation.getId()))
                .build();
    }

    @Transactional(readOnly = true)
    public PublicConversationResponse visitorFetch(String visitorToken) {
        ChatConversation conversation = conversationRepository.findByVisitorToken(visitorToken)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", visitorToken));
        return PublicConversationResponse.builder()
                .conversationId(conversation.getId())
                .visitorToken(conversation.getVisitorToken())
                .messages(loadMessageResponses(conversation.getId()))
                .build();
    }

    // ── AGENT: inbox ──

    @Transactional(readOnly = true)
    public Page<ChatConversationSummary> listConversations(int page, int size) {
        UUID wsId = TenantContext.getWorkspaceId();
        return conversationRepository
                .findByWorkspaceIdOrderByLastMessageAtDesc(wsId, PageRequest.of(page, size))
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return conversationRepository.sumUnreadByTeam(TenantContext.getWorkspaceId());
    }

    @Transactional
    public ChatConversationDetail getConversation(UUID id) {
        UUID wsId = TenantContext.getWorkspaceId();
        ChatConversation conversation = conversationRepository.findByIdAndWorkspaceId(id, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", id));

        // Opening the conversation resets the team unread count
        if (conversation.getUnreadByTeam() > 0) {
            conversation.setUnreadByTeam(0);
            conversationRepository.save(conversation);
        }

        String contactName = null;
        if (conversation.getContactId() != null) {
            contactName = contactRepository.findById(conversation.getContactId())
                    .map(Contact::getFullName)
                    .orElse(null);
        }

        return ChatConversationDetail.builder()
                .id(conversation.getId())
                .visitorName(conversation.getVisitorName())
                .visitorEmail(conversation.getVisitorEmail())
                .contactId(conversation.getContactId())
                .contactName(contactName)
                .status(conversation.getStatus().name())
                .assignedUserId(conversation.getAssignedUserId())
                .sourceUrl(conversation.getSourceUrl())
                .messages(loadMessageResponses(conversation.getId()))
                .createdAt(conversation.getCreatedAt())
                .closedAt(conversation.getClosedAt())
                .build();
    }

    @Transactional
    public ChatMessageResponse agentSend(UUID conversationId, AgentSendMessageRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        UUID userId = TenantContext.getUserId();
        ChatConversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (conversation.getStatus() == ChatConversation.Status.CLOSED) {
            throw new BusinessException("This conversation is closed");
        }

        // Auto-assign when first agent replies
        if (conversation.getAssignedUserId() == null) {
            conversation.setAssignedUserId(userId);
        }

        ChatMessage message = persistMessage(conversation, ChatMessage.Sender.AGENT, userId, request.getMessage());
        touchConversation(conversation, message.getBody(), false, true);

        ChatMessageResponse response = toMessageResponse(message);
        broadcast(conversation, response);
        return response;
    }

    @Transactional
    public void closeConversation(UUID conversationId) {
        UUID wsId = TenantContext.getWorkspaceId();
        ChatConversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        if (conversation.getStatus() == ChatConversation.Status.CLOSED) return;

        conversation.setStatus(ChatConversation.Status.CLOSED);
        conversation.setClosedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        // Notify the visitor (if connected) that the conversation has been closed
        ChatMessageResponse systemMessage = toMessageResponse(
                persistMessage(conversation, ChatMessage.Sender.SYSTEM, null, "Conversation closed."));
        broadcast(conversation, systemMessage);
    }

    // ── Widget config (agent) ──

    @Transactional
    public ChatWidgetConfig getWorkspaceWidget() {
        UUID wsId = TenantContext.getWorkspaceId();
        ChatWidget widget = getOrCreateWidget(wsId);
        Workspace workspace = workspaceRepository.findById(wsId).orElseThrow();
        return ChatWidgetConfig.builder()
                .workspaceSlug(workspace.getSlug())
                .workspaceName(workspace.getName())
                .enabled(widget.isEnabled())
                .displayName(widget.getDisplayName())
                .greeting(widget.getGreeting())
                .offlineMessage(widget.getOfflineMessage())
                .primaryColor(widget.getPrimaryColor())
                .position(widget.getPosition())
                .requireEmail(widget.isRequireEmail())
                .build();
    }

    @Transactional
    public ChatWidgetConfig updateWorkspaceWidget(UpdateWidgetRequest request) {
        UUID wsId = TenantContext.getWorkspaceId();
        ChatWidget widget = getOrCreateWidget(wsId);
        if (request.getEnabled() != null) widget.setEnabled(request.getEnabled());
        if (notBlank(request.getDisplayName())) widget.setDisplayName(request.getDisplayName().trim());
        if (request.getGreeting() != null) widget.setGreeting(request.getGreeting());
        if (request.getOfflineMessage() != null) widget.setOfflineMessage(request.getOfflineMessage());
        if (notBlank(request.getPrimaryColor())) widget.setPrimaryColor(request.getPrimaryColor().trim());
        if (notBlank(request.getPosition())) widget.setPosition(request.getPosition().trim().toUpperCase());
        if (request.getRequireEmail() != null) widget.setRequireEmail(request.getRequireEmail());
        widgetRepository.save(widget);
        return getWorkspaceWidget();
    }

    // ── Internals ──

    private ChatWidget getOrCreateWidget(UUID workspaceId) {
        return widgetRepository.findByWorkspaceId(workspaceId).orElseGet(() -> {
            ChatWidget widget = ChatWidget.builder().build();
            widget.setWorkspaceId(workspaceId);
            return widgetRepository.save(widget);
        });
    }

    /** Link conversation to a Contact if we have an email — creates the contact if needed. */
    private void linkContact(ChatConversation conversation) {
        if (conversation.getContactId() != null) return;
        String email = conversation.getVisitorEmail();
        if (!notBlank(email)) return;

        UUID wsId = conversation.getWorkspaceId();

        // Reuse an existing contact with the same email if present
        Optional<Contact> existing = contactEmailRepository.findAll().stream()
                .filter(ce -> email.equalsIgnoreCase(ce.getEmail()))
                .filter(ce -> Objects.equals(ce.getWorkspaceId(), wsId))
                .findFirst()
                .map(ContactEmail::getContact);

        Contact contact = existing.orElseGet(() -> createChatContact(wsId, conversation, email));
        conversation.setContactId(contact.getId());
        conversationRepository.save(conversation);
    }

    private Contact createChatContact(UUID workspaceId, ChatConversation conversation, String email) {
        String[] parts = splitName(conversation.getVisitorName());
        Contact contact = Contact.builder()
                .firstName(parts[0] != null ? parts[0] : "Visitor")
                .lastName(parts[1])
                .source(ContactSource.CHAT)
                .lifecycleStage(LifecycleStage.LEAD)
                .build();
        contact.setWorkspaceId(workspaceId);

        ContactEmail emailEntity = ContactEmail.builder()
                .email(email)
                .emailType(ContactEmail.EmailType.WORK)
                .primary(true)
                .build();
        emailEntity.setWorkspaceId(workspaceId);
        contact.addEmail(emailEntity);

        return contactRepository.save(contact);
    }

    private void appendVisitorMessage(ChatConversation conversation, String body) {
        ChatMessage message = persistMessage(conversation, ChatMessage.Sender.VISITOR, null, body);
        touchConversation(conversation, body, true, false);

        ChatMessageResponse response = toMessageResponse(message);
        broadcast(conversation, response);

        // Alert the workspace team
        notificationService.notifyWorkspace(
                conversation.getWorkspaceId(),
                NotificationType.GENERIC,
                "New chat message",
                (notBlank(conversation.getVisitorName()) ? conversation.getVisitorName() : "A visitor")
                        + ": " + truncate(body, 120),
                "/chat?conversation=" + conversation.getId(),
                Map.of("conversationId", conversation.getId())
        );

        // Webhook fan-out
        webhookService.publish(conversation.getWorkspaceId(), WebhookEventType.CHAT_MESSAGE_RECEIVED, Map.of(
                "conversationId", conversation.getId(),
                "visitorName", conversation.getVisitorName() != null ? conversation.getVisitorName() : "",
                "visitorEmail", conversation.getVisitorEmail() != null ? conversation.getVisitorEmail() : "",
                "contactId", conversation.getContactId() != null ? conversation.getContactId().toString() : "",
                "body", body,
                "sourceUrl", conversation.getSourceUrl() != null ? conversation.getSourceUrl() : ""
        ));
    }

    private ChatMessage persistMessage(ChatConversation conversation,
                                        ChatMessage.Sender sender, UUID agentUserId, String body) {
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversation.getId())
                .sender(sender)
                .agentUserId(agentUserId)
                .body(body)
                .build();
        message.setWorkspaceId(conversation.getWorkspaceId());
        return messageRepository.save(message);
    }

    private void touchConversation(ChatConversation conversation, String previewSource,
                                    boolean incUnreadByTeam, boolean incUnreadByVisitor) {
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setLastMessagePreview(truncate(previewSource, 500));
        if (incUnreadByTeam) conversation.setUnreadByTeam(conversation.getUnreadByTeam() + 1);
        if (incUnreadByVisitor) conversation.setUnreadByVisitor(conversation.getUnreadByVisitor() + 1);
        conversationRepository.save(conversation);
    }

    /** Broadcast a single message to BOTH the visitor topic and the workspace topic. */
    private void broadcast(ChatConversation conversation, ChatMessageResponse message) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/chat/visitor/" + conversation.getVisitorToken(), message);
            messagingTemplate.convertAndSend(
                    "/topic/chat/workspace/" + conversation.getWorkspaceId(), message);
        } catch (Exception e) {
            log.warn("Chat broadcast failed for message {}: {}", message.getId(), e.getMessage());
        }
    }

    private List<ChatMessageResponse> loadMessageResponses(UUID conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private ChatConversationSummary toSummary(ChatConversation c) {
        return ChatConversationSummary.builder()
                .id(c.getId())
                .visitorName(c.getVisitorName())
                .visitorEmail(c.getVisitorEmail())
                .contactId(c.getContactId())
                .status(c.getStatus().name())
                .assignedUserId(c.getAssignedUserId())
                .unreadByTeam(c.getUnreadByTeam())
                .lastMessagePreview(c.getLastMessagePreview())
                .lastMessageAt(c.getLastMessageAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        String agentName = null;
        if (message.getAgentUserId() != null) {
            agentName = userRepository.findById(message.getAgentUserId())
                    .map(User::getName)
                    .orElse(null);
        }
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .sender(message.getSender().name())
                .agentUserId(message.getAgentUserId())
                .agentName(agentName)
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private static String[] splitName(String fullName) {
        if (!notBlank(fullName)) return new String[] { null, null };
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) return new String[] { trimmed, null };
        return new String[] { trimmed.substring(0, space), trimmed.substring(space + 1).trim() };
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
