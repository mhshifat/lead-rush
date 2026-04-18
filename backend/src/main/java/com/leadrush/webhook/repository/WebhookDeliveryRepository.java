package com.leadrush.webhook.repository;

import com.leadrush.webhook.entity.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    /** Scheduler hot path — fetch up to `limit` deliveries ready to try. */
    @Query("""
        SELECT d FROM WebhookDelivery d
        WHERE d.status IN (com.leadrush.webhook.entity.WebhookDelivery.Status.PENDING,
                           com.leadrush.webhook.entity.WebhookDelivery.Status.FAILED)
          AND d.nextAttemptAt <= :now
        ORDER BY d.nextAttemptAt ASC
    """)
    List<WebhookDelivery> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    /** Delivery log for an endpoint — newest first. */
    Page<WebhookDelivery> findByEndpointIdOrderByCreatedAtDesc(UUID endpointId, Pageable pageable);
}
