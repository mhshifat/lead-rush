package com.leadrush.email.tracking;

import com.leadrush.common.TenantEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Logs each click on a link in a sent email.
 * One step_execution can have many clicks (user clicks link multiple times).
 */
@Entity
@Table(name = "email_link_clicks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailLinkClick extends TenantEntity {

    @Column(name = "step_execution_id", nullable = false)
    private UUID stepExecutionId;

    @Column(name = "clicked_url", nullable = false, columnDefinition = "text")
    private String clickedUrl;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "clicked_at", nullable = false)
    @Builder.Default
    private LocalDateTime clickedAt = LocalDateTime.now();
}
