package com.leadrush.analytics.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverview {

    // Contacts
    private long totalContacts;
    private long contactsAddedLast7Days;

    // Sequences
    private long activeSequences;
    private long totalEnrollments;

    // Email activity (last 30 days)
    private long emailsSent;
    private long emailsOpened;
    private long emailsClicked;
    private long emailsReplied;

    // Rates (0.0 to 1.0)
    private double openRate;
    private double clickRate;
    private double replyRate;

    // Tasks
    private long pendingTasks;
}
