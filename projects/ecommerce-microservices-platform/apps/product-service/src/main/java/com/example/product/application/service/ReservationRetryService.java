package com.example.product.application.service;

import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.repository.StockReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

/**
 * Restock retry leg of the reservation saga (TASK-BE-428, AC-4). When stock for a variant
 * increases (operator adjust or WMS inventory-received), every BACKORDERED reservation holding
 * that variant is re-attempted in FIFO order ({@code created_at ASC}).
 *
 * <p><b>Per-reservation isolation.</b> Each re-attempt runs in its OWN transaction
 * ({@link Propagation#REQUIRES_NEW}) so an optimistic-lock conflict or a still-short reservation
 * cannot abort the others (Failure Scenario "재시도 폭주"). Because Spring AOP does not honour a
 * {@code REQUIRES_NEW} self-invocation, the per-reservation method lives on a SEPARATE bean
 * ({@link Attempt}) injected here — the proxy boundary is crossed so the new transaction
 * actually starts. The status is re-checked inside that transaction (BACKORDERED only) to
 * serialize against a concurrent cancel (Failure Scenario "취소 후 재입고 경합").
 */
@Slf4j
@Service
public class ReservationRetryService {

    private final StockReservationRepository reservationRepository;
    private final Attempt attempt;

    public ReservationRetryService(StockReservationRepository reservationRepository, Attempt attempt) {
        this.reservationRepository = reservationRepository;
        this.attempt = attempt;
    }

    /**
     * Re-attempt the FIFO-ordered BACKORDERED reservations holding {@code variantId}. Called
     * after a POSITIVE stock increase. Each reservation is isolated in its own transaction;
     * one failure does not stop the loop.
     *
     * <p>If the caller is inside a transaction (operator adjust / WMS reconcile), the retry is
     * deferred to {@code afterCommit} so the per-reservation {@code REQUIRES_NEW} attempts read
     * the COMMITTED stock increase (a {@code REQUIRES_NEW} started while the trigger tx is still
     * open would suspend it and read the OLD stock — the retry would no-op until the next
     * trigger). With no active transaction it runs inline.
     */
    public void onStockIncreased(UUID variantId) {
        if (variantId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    retryWaiting(variantId);
                }
            });
            return;
        }
        retryWaiting(variantId);
    }

    private void retryWaiting(UUID variantId) {
        List<StockReservation> waiting = reservationRepository.findBackorderedHoldingVariant(variantId);
        if (waiting.isEmpty()) {
            return;
        }
        log.debug("Restock retry triggered. variantId={}, waiting={}", variantId, waiting.size());
        for (StockReservation reservation : waiting) {
            try {
                attempt.reattempt(reservation.getOrderId());
            } catch (OptimisticLockingFailureException e) {
                // Concurrent reserve/cancel on this order — leave it; the next restock trigger
                // re-attempts. Isolated so the remaining waiting reservations still proceed.
                log.debug("Restock re-attempt lost optimistic-lock race, skipping. orderId={}",
                        reservation.getOrderId());
            } catch (RuntimeException e) {
                log.warn("Restock re-attempt failed, isolating and continuing. orderId={}",
                        reservation.getOrderId(), e);
            }
        }
    }

    /**
     * Per-reservation transactional unit. A separate bean so {@code REQUIRES_NEW} crosses the
     * proxy boundary (a self-invocation from {@link ReservationRetryService} would be ignored
     * by Spring AOP and silently join the caller's transaction).
     */
    @Service
    public static class Attempt {

        private final StockReservationRepository reservationRepository;
        private final ReservationService reservationService;

        public Attempt(StockReservationRepository reservationRepository,
                       ReservationService reservationService) {
            this.reservationRepository = reservationRepository;
            this.reservationService = reservationService;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void reattempt(String orderId) {
            StockReservation reservation = reservationRepository.findByOrderId(orderId).orElse(null);
            // Re-check status INSIDE the new transaction: a concurrent cancel may have released it,
            // or a prior re-attempt in this same loop may have reserved it.
            if (reservation == null || !reservation.isBackordered()) {
                return;
            }
            reservationService.tryReserve(reservation);
        }
    }
}
