package com.example.order.application.service;

import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * Anonymizes order-held PII (the shipping-address snapshot) for a deleted account, in
 * reaction to an IAM {@code account.deleted(anonymized=true)} lifecycle event
 * (ADR-MONO-037 P3-B — the standing TASK-BE-258 GDPR consumer obligation for the order
 * store; the deferred sibling of the user-service profile anonymization, TASK-BE-388 M3).
 *
 * <p>Masking is retention-wide: it reaches EVERY order the subject placed, in any status
 * and across every tenant ({@link OrderRepository#findAllByUserIdAcrossTenants}). Only the
 * address PII is tombstoned — {@code orderId} / {@code userId} (FK), amounts, line items,
 * status, and payment/refund timestamps are preserved (audit / finance / settlement
 * integrity).
 *
 * <p>Idempotent + fail-soft (ADR-MONO-037 P5): a subject with no orders is a no-op; an
 * order whose address is absent or already anonymized is skipped; re-delivery converges
 * (re-masking a tombstoned address is a no-op). No new dedup store is needed — the
 * consumer's {@code EventDeduplicationChecker} guards exact re-delivery and the reaction
 * is naturally idempotent over the monotonic transition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPiiAnonymizationService {

    private final OrderRepository orderRepository;
    private final Clock clock;

    @Transactional
    public void anonymizeOrdersForAccount(String userId) {
        List<Order> orders = orderRepository.findAllByUserIdAcrossTenants(userId);
        if (orders.isEmpty()) {
            log.info("anonymizeOrdersForAccount: no orders for userId={}, no-op "
                    + "(idempotent/fail-soft, ADR-MONO-037 P3-B/P5)", userId);
            return;
        }

        List<Order> masked = orders.stream()
                .filter(order -> order.anonymizePii(clock))
                .toList();

        if (masked.isEmpty()) {
            log.info("anonymizeOrdersForAccount: all {} order(s) for userId={} already anonymized, "
                    + "no-op (idempotent re-delivery)", orders.size(), userId);
            return;
        }

        orderRepository.saveAll(masked);
        log.info("Anonymized order-held PII for userId={}: maskedOrders={}/{} "
                + "(account.deleted anonymized=true, TASK-BE-258 obligation, ADR-MONO-037 P3-B)",
                userId, masked.size(), orders.size());
    }
}
