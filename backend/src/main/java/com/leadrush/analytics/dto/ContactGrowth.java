package com.leadrush.analytics.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Daily new-contact counts for the last N days, plus a lifecycle snapshot.
 * Days with zero new contacts are included so the chart renders without gaps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContactGrowth {

    private int windowDays;
    private long totalAdded;
    private List<DailyPoint> series;
    private List<LifecycleSlice> byLifecycle;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyPoint {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate date;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LifecycleSlice {
        private String stage;
        private long count;
    }
}
