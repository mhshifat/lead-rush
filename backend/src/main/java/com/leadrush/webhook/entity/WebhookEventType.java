package com.leadrush.webhook.entity;

import java.util.Arrays;
import java.util.List;

/**
 * Event types customers can subscribe to.
 *
 * The string form (e.g., "contact.created") is what appears in the DB
 * `webhook_endpoints.events` column + the delivered payload's `type` field.
 *
 * Add new events here + fire them from the relevant service. Nothing else
 * needs to change on the webhook side.
 */
public enum WebhookEventType {
    CONTACT_CREATED("contact.created"),
    CONTACT_UPDATED("contact.updated"),

    DEAL_CREATED("deal.created"),
    DEAL_MOVED("deal.moved"),
    DEAL_WON("deal.won"),
    DEAL_LOST("deal.lost"),

    ENROLLMENT_CREATED("sequence.enrollment.created"),
    ENROLLMENT_COMPLETED("sequence.enrollment.completed"),
    ENROLLMENT_UNSUBSCRIBED("sequence.enrollment.unsubscribed"),
    ENROLLMENT_BOUNCED("sequence.enrollment.bounced"),

    EMAIL_OPENED("email.opened"),
    EMAIL_CLICKED("email.clicked"),

    FORM_SUBMITTED("form.submitted"),
    LEAD_SCORE_THRESHOLD("lead.score_threshold"),

    EMAIL_REPLIED("email.replied"),

    CHAT_CONVERSATION_STARTED("chat.conversation.started"),
    CHAT_MESSAGE_RECEIVED("chat.message.received"),

    TEST("test.ping");

    private final String topic;

    WebhookEventType(String topic) {
        this.topic = topic;
    }

    public String topic() { return topic; }

    public static List<String> allTopics() {
        return Arrays.stream(values()).map(WebhookEventType::topic).toList();
    }
}
