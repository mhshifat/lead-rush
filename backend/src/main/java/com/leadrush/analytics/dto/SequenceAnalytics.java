package com.leadrush.analytics.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SequenceAnalytics {

    private UUID sequenceId;
    private String sequenceName;
    private String status;

    private long totalEnrolled;
    private long activeEnrollments;
    private long completedEnrollments;
    private long unsubscribedEnrollments;

    private long emailsSent;
    private long emailsOpened;
    private long emailsClicked;
    private long emailsReplied;

    private double openRate;
    private double clickRate;
    private double replyRate;
}
