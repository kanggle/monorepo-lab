package com.example.shipping.infrastructure.webhook;

import com.example.shipping.application.port.WebhookDeliveryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * {@link WebhookDeliveryStore} backed by the {@code processed_carrier_webhooks} table
 * (TASK-BE-294): an {@code existsById} fast-path plus an insert guarded by the PK. Runs in
 * the caller's transaction so a failed webhook processing rolls back the registration too.
 *
 * <p><b>Concurrency (corrected in TASK-BE-541).</b> This class previously carried a
 * {@code catch (DataIntegrityViolationException)} that claimed to report a concurrent
 * duplicate as already-seen. That catch was unreachable: the entity has an assigned
 * {@code @Id}, so the INSERT is queued until the commit-time flush, which runs after
 * {@link #registerIfFirst(String)} returns. The Javadoc's own "runs in the caller's
 * transaction" was precisely the reason — there is no inner commit to surface the violation
 * early.
 *
 * <p>What actually happens on a concurrent duplicate: the loser's flush fails, the request
 * transaction rolls back, and — because shipping-service has no
 * {@code @ExceptionHandler(DataIntegrityViolationException.class)} — the caller receives a
 * <b>500</b>. The carrier's own retry then hits the {@code existsById} fast-path and gets a
 * clean result, so no delivery is lost, but the 500 is real and is NOT fixed here.
 * Translating it into a proper 4xx is {@code TASK-BE-542} (the fleet-declared
 * {@code DATA_INTEGRITY_VIOLATION} mapping); catching in-transaction cannot help, because a
 * flushed violation marks the transaction rollback-only and "catch and carry on" is
 * unavailable at this layer.
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
        // See the class Javadoc: no try/catch, because one here cannot fire. The PK is the
        // real arbiter for the concurrent case; the loser's transaction rolls back and the
        // carrier's retry takes the existsById fast-path above.
        repository.save(ProcessedCarrierWebhookJpaEntity.of(deliveryId, Instant.now(clock)));
        return true;
    }
}
