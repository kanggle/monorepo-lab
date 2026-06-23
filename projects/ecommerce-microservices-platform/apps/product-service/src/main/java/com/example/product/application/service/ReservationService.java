package com.example.product.application.service;

import com.example.product.domain.event.OrderReservationFailedPayload;
import com.example.product.domain.event.OrderReservationFailedPayload.Shortage;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.model.reservation.StockReservationLine;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Payment-driven stock-reservation saga (TASK-BE-428). Converges two independently-ordered
 * inputs — the {@code OrderPlaced} line snapshot and the {@code PaymentCompleted} signal — and,
 * once both are present, performs an ALL-OR-NOTHING reserve in a single transaction:
 *
 * <ul>
 *   <li>every line sufficient → decrement each (optimistic lock) → {@code RESERVED} +
 *       {@code StockChanged(ORDER_RESERVED, orderId)} per line (order-service confirms);</li>
 *   <li>any line short → NO decrement on any line → {@code BACKORDERED} +
 *       {@code OrderReservationFailed} (order-service backorders).</li>
 * </ul>
 *
 * <p>Cancellation ({@code OrderCancelled}) releases the reservation: a previously RESERVED
 * order restores stock + emits {@code StockChanged(ORDER_CANCELLED)} per line; NEW/BACKORDERED
 * release without any stock change. Idempotent on an already-RELEASED reservation.
 *
 * <p>Reservation reason constants reuse the {@code StockChanged} contract reasons.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    static final String REASON_ORDER_RESERVED = "ORDER_RESERVED";
    static final String REASON_ORDER_CANCELLED = "ORDER_CANCELLED";
    static final String REASON_INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

    private final StockReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final EventPublishingHelper eventPublishingHelper;
    private final Clock clock;

    /**
     * OrderPlaced leg: create the reservation with its lines (or fill a payment-first stub),
     * then reserve if both inputs have converged.
     */
    @Transactional
    public void recordOrderPlaced(String orderId, String tenantId, List<StockReservationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            log.warn("OrderPlaced has no reservable lines, skipping. orderId={}", orderId);
            return;
        }
        Instant now = Instant.now(clock);
        StockReservation reservation = reservationRepository.findByOrderId(orderId)
                .map(existing -> {
                    existing.applyLines(lines, now);
                    return existing;
                })
                .orElseGet(() -> StockReservation.fromOrderPlaced(orderId, tenantId, lines, now));
        reservation = reservationRepository.save(reservation);
        reserveIfReady(reservation);
    }

    /**
     * PaymentCompleted leg: mark payment received on the reservation (or create a stub if
     * payment arrived before OrderPlaced), then reserve if both inputs have converged.
     */
    @Transactional
    public void recordPaymentCompleted(String orderId, String tenantId) {
        Instant now = Instant.now(clock);
        StockReservation reservation = reservationRepository.findByOrderId(orderId)
                .map(existing -> {
                    existing.markPaymentReceived(now);
                    return existing;
                })
                .orElseGet(() -> StockReservation.stubFromPayment(orderId, tenantId, now));
        reservation = reservationRepository.save(reservation);
        reserveIfReady(reservation);
    }

    private void reserveIfReady(StockReservation reservation) {
        if (reservation.isReadyToReserve()) {
            tryReserve(reservation);
        }
    }

    /**
     * ALL-OR-NOTHING reserve. Loads every line's inventory; if ANY line is short, sets
     * BACKORDERED and emits {@code OrderReservationFailed} WITHOUT decrementing anything.
     * Otherwise decrements every line, sets RESERVED, and emits {@code StockChanged(ORDER_RESERVED)}
     * per line. Runs in the caller's transaction (a partial decrement that then fails rolls the
     * whole attempt back — Failure Scenario "부분 차감 후 중단").
     */
    void tryReserve(StockReservation reservation) {
        Instant now = Instant.now(clock);
        List<LineWithInventory> resolved = new ArrayList<>();
        List<Shortage> shortages = new ArrayList<>();

        for (StockReservationLine line : reservation.getLines()) {
            Optional<Inventory> inventory = inventoryRepository.findByVariantId(line.variantId());
            int available = inventory.map(inv -> inv.currentStock().value()).orElse(0);
            if (inventory.isEmpty() || available < line.quantity()) {
                shortages.add(new Shortage(line.variantId().toString(), line.quantity(), available));
            } else {
                resolved.add(new LineWithInventory(line, inventory.get()));
            }
        }

        if (!shortages.isEmpty()) {
            reservation.markBackordered(now);
            reservationRepository.save(reservation);
            eventPublishingHelper.publishSafely(
                    ProductEvent.orderReservationFailed(new OrderReservationFailedPayload(
                            reservation.getOrderId(), REASON_INSUFFICIENT_STOCK, shortages)),
                    "reservation", reservation.getOrderId());
            log.info("Reservation backordered (insufficient stock). orderId={}, shortLines={}",
                    reservation.getOrderId(), shortages.size());
            return;
        }

        for (LineWithInventory lwi : resolved) {
            decrementAndPublish(reservation.getOrderId(), lwi.line(), lwi.inventory());
        }
        reservation.markReserved(now);
        reservationRepository.save(reservation);
        log.info("Reservation reserved. orderId={}, lines={}",
                reservation.getOrderId(), resolved.size());
    }

    private void decrementAndPublish(String orderId, StockReservationLine line, Inventory inventory) {
        int previousStock = inventory.currentStock().value();
        inventory.decrease(line.quantity());
        int currentStock = inventory.currentStock().value();
        inventoryRepository.save(inventory);
        eventPublishingHelper.publishSafely(
                ProductEvent.stockChanged(new StockChangedPayload(
                        line.productId().toString(), line.variantId().toString(),
                        previousStock, currentStock, -line.quantity(), REASON_ORDER_RESERVED, orderId)),
                "variant(reservation)", line.variantId());
    }

    /**
     * OrderCancelled leg: release the reservation. RESERVED → restore stock per line +
     * {@code StockChanged(ORDER_CANCELLED)}; NEW/BACKORDERED → release with no stock change;
     * already RELEASED → idempotent no-op. The status re-check inside the transaction serializes
     * against a concurrent restock-retry (Failure Scenario "취소 후 재입고 경합").
     */
    @Transactional
    public void release(String orderId) {
        Optional<StockReservation> found = reservationRepository.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.debug("OrderCancelled for unknown reservation, ignoring. orderId={}", orderId);
            return;
        }
        StockReservation reservation = found.get();
        if (reservation.isReleased()) {
            return; // idempotent
        }
        Instant now = Instant.now(clock);
        if (reservation.isReserved()) {
            for (StockReservationLine line : reservation.getLines()) {
                restoreAndPublish(orderId, line);
            }
        }
        reservation.markReleased(now);
        reservationRepository.save(reservation);
        log.info("Reservation released on cancel. orderId={}", orderId);
    }

    private void restoreAndPublish(String orderId, StockReservationLine line) {
        Optional<Inventory> inventory = inventoryRepository.findByVariantId(line.variantId());
        if (inventory.isEmpty()) {
            log.warn("Cannot restore stock for cancelled order — variant gone. orderId={}, variantId={}",
                    orderId, line.variantId());
            return;
        }
        Inventory inv = inventory.get();
        int previousStock = inv.currentStock().value();
        inv.increase(line.quantity());
        int currentStock = inv.currentStock().value();
        inventoryRepository.save(inv);
        eventPublishingHelper.publishSafely(
                ProductEvent.stockChanged(new StockChangedPayload(
                        line.productId().toString(), line.variantId().toString(),
                        previousStock, currentStock, line.quantity(), REASON_ORDER_CANCELLED, orderId)),
                "variant(reservation-cancel)", line.variantId());
    }

    private record LineWithInventory(StockReservationLine line, Inventory inventory) {}
}
