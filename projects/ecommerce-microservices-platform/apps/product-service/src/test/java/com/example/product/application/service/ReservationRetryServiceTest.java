package com.example.product.application.service;

import com.example.product.domain.model.reservation.ReservationStatus;
import com.example.product.domain.model.reservation.StockReservation;
import com.example.product.domain.model.reservation.StockReservationLine;
import com.example.product.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for the restock-retry FIFO leg (TASK-BE-428, AC-4). Drives the retry against a real
 * {@link ReservationService} + an in-memory store so the FIFO + partial-restock semantics are
 * exercised end-to-end. No active transaction in the unit test → the retry runs inline (the
 * afterCommit deferral is exercised by the integration test).
 */
@DisplayName("ReservationRetryService 단위 테스트 (TASK-BE-428 재입고 FIFO 재시도)")
class ReservationRetryServiceTest {

    private InMemoryReservationRepository reservationRepository;
    private FakeInventory inventory;
    private ReservationService reservationService;
    private ReservationRetryService retryService;

    private final UUID variant = UUID.randomUUID();
    private final UUID product = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reservationRepository = new InMemoryReservationRepository();
        inventory = new FakeInventory();
        EventPublishingHelper publisher = mock(EventPublishingHelper.class);
        reservationService = new ReservationService(
                reservationRepository, inventory.repository(), publisher,
                java.time.Clock.systemUTC());
        retryService = new ReservationRetryService(
                reservationRepository,
                new ReservationRetryService.Attempt(reservationRepository, reservationService));
    }

    private StockReservation backordered(int qty, Instant createdAt) {
        StockReservation r = StockReservation.reconstitute(
                UUID.randomUUID(), UUID.randomUUID().toString(), "ecommerce",
                ReservationStatus.BACKORDERED, true,
                List.of(new StockReservationLine(variant, product, qty)),
                createdAt, createdAt);
        reservationRepository.save(r);
        return r;
    }

    @Test
    @DisplayName("AC-4 FIFO: 백오더 A(needs 5)·B(needs 3), +6 입고 → 오래된 A만 충족, B는 BACKORDERED 유지")
    void partialRestock_satisfiesOlderOnly_fifo() {
        Instant t0 = Instant.parse("2026-06-23T09:00:00Z");
        StockReservation older = backordered(5, t0);                  // A, created first
        StockReservation newer = backordered(3, t0.plusSeconds(60));  // B, created later
        inventory.set(variant, 6); // enough for A (5) but not for B afterwards (1 < 3)

        retryService.onStockIncreased(variant);

        assertThat(reservationRepository.findByOrderId(older.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationRepository.findByOrderId(newer.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.BACKORDERED);
        assertThat(inventory.get(variant)).isEqualTo(1); // 6 - 5
    }

    @Test
    @DisplayName("AC-4 충분 입고 → 두 백오더 모두 RESERVED")
    void fullRestock_satisfiesAll() {
        Instant t0 = Instant.parse("2026-06-23T09:00:00Z");
        StockReservation a = backordered(5, t0);
        StockReservation b = backordered(3, t0.plusSeconds(60));
        inventory.set(variant, 10);

        retryService.onStockIncreased(variant);

        assertThat(reservationRepository.findByOrderId(a.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(reservationRepository.findByOrderId(b.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RESERVED);
        assertThat(inventory.get(variant)).isEqualTo(2); // 10 - 5 - 3
    }

    @Test
    @DisplayName("대기 백오더 없으면 no-op")
    void noBackordered_noOp() {
        inventory.set(variant, 100);
        retryService.onStockIncreased(variant); // nothing waiting
        assertThat(inventory.get(variant)).isEqualTo(100);
    }

    // ─── fakes ──────────────────────────────────────────────────────────────

    /** In-memory variant→stock backing a mocked InventoryRepository. */
    private static final class FakeInventory {
        private final java.util.Map<UUID, Integer> stock = new java.util.HashMap<>();
        private final com.example.product.domain.repository.InventoryRepository repo =
                mock(com.example.product.domain.repository.InventoryRepository.class);

        FakeInventory() {
            given(repo.findByVariantId(any())).willAnswer(inv -> {
                UUID id = inv.getArgument(0);
                return stock.containsKey(id)
                        ? Optional.of(com.example.product.domain.model.Inventory.create(
                                id, new com.example.product.domain.model.StockQuantity(stock.get(id))))
                        : Optional.empty();
            });
            willAnswer(inv -> {
                com.example.product.domain.model.Inventory i = inv.getArgument(0);
                stock.put(i.getVariantId(), i.currentStock().value());
                return i;
            }).given(repo).save(any(com.example.product.domain.model.Inventory.class));
        }

        void set(UUID variantId, int value) {
            stock.put(variantId, value);
        }

        int get(UUID variantId) {
            return stock.getOrDefault(variantId, 0);
        }

        com.example.product.domain.repository.InventoryRepository repository() {
            return repo;
        }
    }

    /** Minimal in-memory reservation store, FIFO by createdAt. */
    private static final class InMemoryReservationRepository implements StockReservationRepository {
        private final java.util.Map<String, StockReservation> byOrderId = new java.util.LinkedHashMap<>();

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
            List<StockReservation> result = new ArrayList<>(byOrderId.values().stream()
                    .filter(StockReservation::isBackordered)
                    .filter(r -> r.getLines().stream().anyMatch(l -> l.variantId().equals(variantId)))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList());
            return result;
        }
    }
}
