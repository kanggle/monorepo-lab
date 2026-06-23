package com.example.product.domain.model.reservation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Reservation aggregate for the payment-driven stock-reservation saga (TASK-BE-428).
 *
 * <p>One reservation per {@code orderId} (unique). It converges two independently-ordered
 * inputs — the {@code OrderPlaced} line snapshot and the {@code PaymentCompleted} signal —
 * and fires an all-or-nothing reserve exactly once when BOTH have arrived (lines present AND
 * {@code paymentReceived}) while still {@link ReservationStatus#NEW}. The actual stock check /
 * decrement and event emission live in the application {@code ReservationService}; this
 * aggregate owns the state machine and the "ready to reserve" predicate so the convergence
 * rule cannot drift between the two consumer entry points.
 *
 * <p>Framework-free (domain layer): a plain mutable aggregate reconstituted from / mapped to a
 * JPA entity by the infrastructure layer. Optimistic locking ({@code @Version}) lives on the
 * persistence entity.
 */
public class StockReservation {

    private final UUID id;
    private final String orderId;
    private final String tenantId;
    private ReservationStatus status;
    private boolean paymentReceived;
    private final List<StockReservationLine> lines;
    private final Instant createdAt;
    private Instant updatedAt;

    private StockReservation(UUID id, String orderId, String tenantId, ReservationStatus status,
                             boolean paymentReceived, List<StockReservationLine> lines,
                             Instant createdAt, Instant updatedAt) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        this.id = id;
        this.orderId = orderId;
        this.tenantId = tenantId;
        this.status = status;
        this.paymentReceived = paymentReceived;
        this.lines = new ArrayList<>(lines);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** New reservation from the OrderPlaced leg (lines known, payment not yet seen). */
    public static StockReservation fromOrderPlaced(String orderId, String tenantId,
                                                   List<StockReservationLine> lines, Instant now) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("OrderPlaced reservation must have at least one line");
        }
        return new StockReservation(UUID.randomUUID(), orderId, tenantId, ReservationStatus.NEW,
                false, lines, now, now);
    }

    /** Stub reservation from the PaymentCompleted leg arriving first (payment known, no lines yet). */
    public static StockReservation stubFromPayment(String orderId, String tenantId, Instant now) {
        return new StockReservation(UUID.randomUUID(), orderId, tenantId, ReservationStatus.NEW,
                true, List.of(), now, now);
    }

    /** Reconstitute from persistence. */
    public static StockReservation reconstitute(UUID id, String orderId, String tenantId,
                                                ReservationStatus status, boolean paymentReceived,
                                                List<StockReservationLine> lines,
                                                Instant createdAt, Instant updatedAt) {
        return new StockReservation(id, orderId, tenantId, status, paymentReceived, lines,
                createdAt, updatedAt);
    }

    /** Fill the line snapshot when OrderPlaced arrives after a payment-first stub. */
    public void applyLines(List<StockReservationLine> newLines, Instant now) {
        if (newLines == null || newLines.isEmpty()) {
            throw new IllegalArgumentException("cannot apply empty line snapshot");
        }
        if (!this.lines.isEmpty()) {
            // Idempotent re-delivery of OrderPlaced: lines already captured, no-op.
            return;
        }
        this.lines.addAll(newLines);
        this.updatedAt = now;
    }

    /** Mark the payment signal received (PaymentCompleted leg). Idempotent. */
    public void markPaymentReceived(Instant now) {
        if (!this.paymentReceived) {
            this.paymentReceived = true;
            this.updatedAt = now;
        }
    }

    /**
     * Both inputs have converged and the reservation has never been reserved/released —
     * the single guarded condition under which a reserve attempt fires (AC-2: exactly once).
     */
    public boolean isReadyToReserve() {
        return status == ReservationStatus.NEW && paymentReceived && !lines.isEmpty();
    }

    public void markReserved(Instant now) {
        this.status = ReservationStatus.RESERVED;
        this.updatedAt = now;
    }

    public void markBackordered(Instant now) {
        this.status = ReservationStatus.BACKORDERED;
        this.updatedAt = now;
    }

    public void markReleased(Instant now) {
        this.status = ReservationStatus.RELEASED;
        this.updatedAt = now;
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public boolean isBackordered() {
        return status == ReservationStatus.BACKORDERED;
    }

    public boolean isReleased() {
        return status == ReservationStatus.RELEASED;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public boolean isPaymentReceived() {
        return paymentReceived;
    }

    public List<StockReservationLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
