package com.example.shipping.infrastructure.webhook;

import com.example.shipping.application.port.WebhookDeliveryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * {@link WebhookDeliveryStore} backed by the {@code processed_carrier_webhooks} table
 * (TASK-BE-294). Mirrors the established Kafka {@code EventDeduplicationChecker} idiom:
 * an {@code existsById} fast-path plus an insert whose PK collision (a concurrent
 * duplicate) is caught and reported as already-seen. Runs in the caller's transaction so a
 * failed webhook processing rolls back the registration too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaWebhookDeliveryStore implements WebhookDeliveryStore {

    private final ProcessedCarrierWebhookJpaRepository repository;
    private final Clock clock;

    @Override
    public boolean registerIfFirst(String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            log.warn("Carrier webhook delivery id is null/blank; skipping dedup (treated as first)");
            return true;
        }
        if (repository.existsById(deliveryId)) {
            return false;
        }
        try {
            repository.save(ProcessedCarrierWebhookJpaEntity.of(deliveryId, Instant.now(clock)));
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate carrier webhook delivery {}; treated as already-seen", deliveryId);
            return false;
        }
        return true;
    }
}
