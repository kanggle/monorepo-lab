package com.example.erp.notification.application;

import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates one external-channel retry sweep (TASK-ERP-BE-020): find the deliveries due
 * for an attempt ({@code status=PENDING ∧ scheduled_retry_at ≤ now}, oldest-due first, capped
 * at the configured batch size) and process each via {@link DeliveryAttemptProcessor} (which
 * owns the per-delivery transaction). Not transactional itself — a failure on one delivery
 * never rolls back the others. Exposed as a plain method so an integration test can drive a
 * sweep deterministically without the scheduler (the ShedLock-trap-avoidance idiom).
 */
@Slf4j
@Service
public class RetryDeliveryService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryAttemptProcessor attemptProcessor;
    private final ExternalNotificationProperties properties;

    public RetryDeliveryService(NotificationDeliveryRepository deliveryRepository,
                                DeliveryAttemptProcessor attemptProcessor,
                                ExternalNotificationProperties properties) {
        this.deliveryRepository = deliveryRepository;
        this.attemptProcessor = attemptProcessor;
        this.properties = properties;
    }

    /** Process all deliveries due at {@code now}; returns how many were attempted. */
    public int runDue(Instant now) {
        List<String> dueIds = deliveryRepository.findDueDeliveryIds(
                now, properties.getRetry().getBatchSize());
        for (String id : dueIds) {
            try {
                attemptProcessor.attempt(id, now);
            } catch (Exception e) {
                // Per-delivery isolation — one bad delivery never stops the sweep.
                log.warn("External delivery attempt failed for {}: {}", id, e.getMessage());
            }
        }
        return dueIds.size();
    }
}
