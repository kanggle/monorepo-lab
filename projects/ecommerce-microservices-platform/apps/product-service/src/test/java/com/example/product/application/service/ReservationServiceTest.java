package com.example.product.application.service;

import com.example.product.domain.event.OrderReservationFailedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.StockChangedPayload;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.model.StockQuantity;
import com.example.product.domain.model.reservation.ReservationStatus;
import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.model.reservation.StockReservationLine;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the payment-driven reservation saga (TASK-BE-428). A real in-memory
 * reservation store (so the convergence state machine is exercised end-to-end) + mocked
 * inventory and event publisher.
 */
@DisplayName("ReservationService 단위 테스트 (TASK-BE-428 결제기반 재고예약 saga)")
class ReservationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-23T10:00:00Z"), ZoneOffset.UTC);
    private static final String TENANT = "ecommerce";

    private InMemoryReservationRepository reservationRepository;
    private InventoryRepository inventoryRepository;
    private EventPublishingHelper eventPublishingHelper;
    private ReservationService service;

    private final String orderId = UUID.randomUUID().toString();
    private final UUID variantA = UUID.randomUUID();
    private final UUID productA = UUID.randomUUID();
    private final UUID variantB = UUID.randomUUID();
    private final UUID productB = UUID.randomUUID();

    private final Map<UUID, Integer> stock = new HashMap<>();

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        inventoryRepository = mock(InventoryRepository.class);
        eventPublishingHelper = mock(EventPublishingHelper.class);
        service = new ReservationService(reservationRepository, inventoryRepository, eventPublishingHelper, FIXED);
        stock.clear();

        // inventory mock backed by the `stock` map (reflects decrements within a test).
        given(inventoryRepository.findByVariantId(any())).willAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return stock.containsKey(id)
                    ? Optional.of(Inventory.create(id, new StockQuantity(stock.get(id))))
                    : Optional.empty();
        });
        given(inventoryRepository.save(any(Inventory.class))).willAnswer(inv -> {
            Inventory i = inv.getArgument(0);
            stock.put(i.getVariantId(), i.currentStock().value());
            return i;
        });
    }

    private StockReservationLine line(UUID variantId, UUID productId, int qty) {
        return new StockReservationLine(variantId, productId, qty);
    }

    // ─── AC-1: happy path ───────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1 두 입력 모두 도착 + 재고 충분 → RESERVED, 라인별 ORDER_RESERVED, 재고 차감")
    void bothInputs_sufficient_reservesAndDecrements() {
        stock.put(variantA, 10);
        stock.put(variantB, 5);

        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 3), line(variantB, productB, 2)));
        service.recordPaymentCompleted(orderId, TENANT);

        StockReservation saved = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(stock.get(variantA)).isEqualTo(7);
        assertThat(stock.get(variantB)).isEqualTo(3);

        verify(eventPublishingHelper, times(2)).publishSafely(
                argReservedFor(orderId), anyString(), any());
    }

    // ─── AC-2: order-independent convergence ────────────────────────────────

    @Test
    @DisplayName("AC-2 placed → payment 순서로 정확히 1회 reserve")
    void placedThenPayment_reservesOnce() {
        stock.put(variantA, 10);

        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));
        // not reserved yet (no payment)
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.NEW);
        assertThat(stock.get(variantA)).isEqualTo(10);

        service.recordPaymentCompleted(orderId, TENANT);

        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(stock.get(variantA)).isEqualTo(6);
        verify(eventPublishingHelper, times(1)).publishSafely(argReservedFor(orderId), anyString(), any());
    }

    @Test
    @DisplayName("AC-2 payment → placed 순서(payment 선도착 stub)로도 정확히 1회 reserve")
    void paymentThenPlaced_reservesOnce() {
        stock.put(variantA, 10);

        service.recordPaymentCompleted(orderId, TENANT);
        // stub created, no lines yet → not reserved
        StockReservation stub = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(stub.getStatus()).isEqualTo(ReservationStatus.NEW);
        assertThat(stub.isPaymentReceived()).isTrue();
        assertThat(stub.getLines()).isEmpty();

        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));

        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(stock.get(variantA)).isEqualTo(6);
        verify(eventPublishingHelper, times(1)).publishSafely(argReservedFor(orderId), anyString(), any());
    }

    // ─── AC-3: insufficient → whole-order backorder, no decrement ───────────

    @Test
    @DisplayName("AC-3 한 라인이라도 부족 → 어떤 라인도 차감 안 함, BACKORDERED + OrderReservationFailed")
    void oneLineShort_backordersWholeOrderNoDecrement() {
        stock.put(variantA, 10); // sufficient
        stock.put(variantB, 1);  // SHORT (needs 2)

        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 3), line(variantB, productB, 2)));
        service.recordPaymentCompleted(orderId, TENANT);

        StockReservation saved = reservationRepository.findByOrderId(orderId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.BACKORDERED);
        // NO decrement on ANY line (all-or-nothing).
        assertThat(stock.get(variantA)).isEqualTo(10);
        assertThat(stock.get(variantB)).isEqualTo(1);

        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(eventPublishingHelper).publishSafely(argReservationFailed(orderId), anyString(), any());
        verify(eventPublishingHelper, never()).publishSafely(argReservedFor(orderId), anyString(), any());
    }

    // ─── AC-5: release ──────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-5 RESERVED 예약 취소 → 재고 복구 + 라인별 ORDER_CANCELLED, RELEASED")
    void releaseReserved_restoresStockAndEmitsCancelled() {
        stock.put(variantA, 10);
        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));
        service.recordPaymentCompleted(orderId, TENANT);
        assertThat(stock.get(variantA)).isEqualTo(6);

        service.release(orderId);

        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(stock.get(variantA)).isEqualTo(10); // restored
        verify(eventPublishingHelper).publishSafely(argCancelledFor(orderId), anyString(), any());
    }

    @Test
    @DisplayName("AC-5 BACKORDERED 예약 취소 → 재고 변동 없이 RELEASED")
    void releaseBackordered_noStockChange() {
        stock.put(variantA, 1); // short → backorder
        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 5)));
        service.recordPaymentCompleted(orderId, TENANT);
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.BACKORDERED);

        service.release(orderId);

        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(stock.get(variantA)).isEqualTo(1); // unchanged
        verify(eventPublishingHelper, never()).publishSafely(argCancelledFor(orderId), anyString(), any());
    }

    @Test
    @DisplayName("AC-4 (BE-435) NEW 예약 취소 (결제 전 stuck 주문) → 재고 변동 0, StockChanged 미발행, RELEASED")
    void releaseNew_neverReserved_noStockMovementNoEvent() {
        // Stuck payment-pending order: OrderPlaced recorded but payment never completed,
        // so the reservation is still NEW (never reserved → no stock decremented).
        stock.put(variantA, 10);
        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));
        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.NEW);
        assertThat(stock.get(variantA)).isEqualTo(10); // nothing reserved yet

        service.release(orderId); // stuck-detector cancel (PAYMENT_TIMEOUT)

        assertThat(reservationRepository.findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
        assertThat(stock.get(variantA)).isEqualTo(10); // ZERO stock decrement/restore
        // No StockChanged emitted at all (neither ORDER_CANCELLED nor any other reason).
        verify(eventPublishingHelper, never()).publishSafely(argCancelledFor(orderId), anyString(), any());
        verify(eventPublishingHelper, never()).publishSafely(any(), anyString(), any());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("AC-5 미존재 예약 취소 → no-op")
    void releaseUnknown_noOp() {
        service.release(UUID.randomUUID().toString());
        verify(eventPublishingHelper, never()).publishSafely(any(), anyString(), any());
    }

    @Test
    @DisplayName("AC-6 이미 RELEASED 예약 재취소 → 멱등 no-op (재고 재복구 없음)")
    void releaseAlreadyReleased_idempotent() {
        stock.put(variantA, 10);
        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));
        service.recordPaymentCompleted(orderId, TENANT);
        service.release(orderId);
        assertThat(stock.get(variantA)).isEqualTo(10);

        service.release(orderId); // second cancel

        assertThat(stock.get(variantA)).isEqualTo(10); // NOT double-restored
        verify(eventPublishingHelper, times(1)).publishSafely(argCancelledFor(orderId), anyString(), any());
    }

    // ─── AC-6: idempotent double-reserve guard ──────────────────────────────

    @Test
    @DisplayName("AC-6 같은 두 입력 재전달(중복 payment) → status=NEW 가드로 재차감 없음")
    void duplicatePayment_doesNotDoubleReserve() {
        stock.put(variantA, 10);
        service.recordOrderPlaced(orderId, TENANT, List.of(line(variantA, productA, 4)));
        service.recordPaymentCompleted(orderId, TENANT);
        assertThat(stock.get(variantA)).isEqualTo(6);

        // duplicate payment (already RESERVED) — isReadyToReserve() is false.
        service.recordPaymentCompleted(orderId, TENANT);

        assertThat(stock.get(variantA)).isEqualTo(6); // no second decrement
        verify(eventPublishingHelper, times(1)).publishSafely(argReservedFor(orderId), anyString(), any());
    }

    // ─── ArgumentMatchers ───────────────────────────────────────────────────

    private static ProductEvent argReservedFor(String orderId) {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null
                && "StockChanged".equals(e.eventType())
                && e.payload() instanceof StockChangedPayload p
                && "ORDER_RESERVED".equals(p.reason()) && orderId.equals(p.orderId()));
    }

    private static ProductEvent argCancelledFor(String orderId) {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null
                && "StockChanged".equals(e.eventType())
                && e.payload() instanceof StockChangedPayload p
                && "ORDER_CANCELLED".equals(p.reason()) && orderId.equals(p.orderId()));
    }

    private static ProductEvent argReservationFailed(String orderId) {
        return org.mockito.ArgumentMatchers.argThat(e -> e != null
                && "OrderReservationFailed".equals(e.eventType())
                && e.payload() instanceof OrderReservationFailedPayload p
                && orderId.equals(p.orderId()) && "INSUFFICIENT_STOCK".equals(p.reason())
                && !p.shortages().isEmpty());
    }

    /** Minimal in-memory reservation store keyed by orderId (preserves the aggregate identity). */
    private static final class InMemoryReservationRepository implements StockReservationRepository {
        private final Map<String, StockReservation> byOrderId = new HashMap<>();

        @Override
        public StockReservation save(StockReservation reservation) {
            byOrderId.put(reservation.getOrderId(), reservation);
            return reservation;
        }

        @Override
        public Optional<StockReservation> findByOrderId(String orderId) {
            return Optional.ofNullable(byOrderId.get(orderId));
        }

        @Override
        public List<StockReservation> findBackorderedHoldingVariant(UUID variantId) {
            return byOrderId.values().stream()
                    .filter(StockReservation::isBackordered)
                    .filter(r -> r.getLines().stream().anyMatch(l -> l.variantId().equals(variantId)))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
        }
    }
}
