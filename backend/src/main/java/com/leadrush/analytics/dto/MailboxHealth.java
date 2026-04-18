package com.leadrush.analytics.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * Per-mailbox send health for the last 30 days.
 * High bounceRate signals a deliverability problem that needs attention.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MailboxHealth {

    private int windowDays;
    private List<Mailbox> mailboxes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mailbox {
        private UUID mailboxId;
        private String email;
        private String name;
        private String status;

        private Integer dailyLimit;
        private Integer sendsToday;

        private long sent;
        private long failed;
        private long bounced;

        /** bounced / (sent + bounced) — 0 if no volume. */
        private double bounceRate;
    }
}
