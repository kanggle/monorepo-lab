package com.example.product.infrastructure.event;

import com.example.product.application.service.RegisterSellerService;
import com.example.product.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes IAM {@code account.status.changed} → projects the lock into the marketplace
 * (ADR-MONO-042 D4-C, TASK-BE-421): when the backing seller-operator account transitions to
 * {@code LOCKED}, the matching {@link com.example.product.domain.model.Seller} is suspended.
 *
 * <p>This closes the lifecycle hole where a fraud/admin-locked seller stays {@code ACTIVE} in
 * the marketplace. It is the REVERSE of the forward operator-suspend → IAM-lock leg
 * (TASK-BE-402): the projection NEVER calls back to IAM (the account is already locked — IAM
 * is the producer of this event), so there is no loop. A forward {@code suspend} re-emits
 * {@code LOCKED} which loops back here and is an already-SUSPENDED idempotent no-op (logged at
 * DEBUG, never a spammy WARN).
 *
 * <p>Fail-soft (mirrors the WMS reconciliation consumers):
 * <ul>
 *   <li>missing {@code accountId} → WARN + skip.</li>
 *   <li>{@code currentStatus != "LOCKED"} → silent skip (every status transition is emitted; we
 *       only act on LOCKED).</li>
 *   <li>no seller for this account in the tenant → WARN + skip (a locked account need not back a
 *       seller here).</li>
 *   <li>CLOSED seller → caught + WARN, NOT rethrown (terminal-state race tolerated).</li>
 *   <li>malformed JSON → {@link IllegalArgumentException} (not-retryable → DLQ via the shared
 *       error handler).</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class AccountStatusChangedSellerConsumer {

    private static final String LOCKED = "LOCKED";

    private final RegisterSellerService registerSellerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account.status.changed", groupId = "product-service-iam")
    public void onMessage(@Payload String payload) {
        handle(deserialize(payload));
    }

    private AccountStatusChangedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AccountStatusChangedEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize account.status.changed event", e);
        }
    }

    void handle(AccountStatusChangedEvent event) {
        if (event.accountId() == null || event.accountId().isBlank()) {
            log.warn("account.status.changed event missing accountId, skipping.");
            return;
        }

        if (!LOCKED.equals(event.currentStatus())) {
            // Every status transition is emitted; the reverse projection acts ONLY on LOCKED.
            return;
        }

        String tenantId = event.tenantId();
        try {
            TenantContext.set(tenantId);
            boolean suspended = registerSellerService.suspendByLockedAccount(event.accountId());
            if (suspended) {
                log.info("seller suspended via IAM account lock (D4-C) tenant={} accountId={}",
                        tenantId, event.accountId());
            } else {
                // Either no seller backs this account here, or it is already SUSPENDED
                // (idempotent re-delivery / forward suspend→LOCKED loop-back) — not an error.
                log.debug("account.status.changed=LOCKED → no seller transition (not found or "
                        + "already SUSPENDED). tenant={} accountId={}", tenantId, event.accountId());
            }
        } catch (IllegalStateException e) {
            // CLOSED seller is terminal — tolerate the race, do NOT rethrow (no DLQ).
            log.warn("account.status.changed=LOCKED for a CLOSED seller — race tolerated, skipping. "
                    + "tenant={} accountId={} reason={}", tenantId, event.accountId(), e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
