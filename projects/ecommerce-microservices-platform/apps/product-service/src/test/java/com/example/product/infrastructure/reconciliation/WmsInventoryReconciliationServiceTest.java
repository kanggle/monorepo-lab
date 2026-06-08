package com.example.product.infrastructure.reconciliation;

import com.example.product.application.service.EventPublishingHelper;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.model.Inventory;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.InventoryRepository.VariantRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("WmsInventoryReconciliationService 단위 테스트 (ADR-MONO-022 §D4 v2(b))")
class WmsInventoryReconciliationServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-08T10:00:00Z"), ZoneOffset.UTC);

    private WmsSkuSnapshotRepository skuSnapshotRepository;
    private WmsInventoryAvailableRepository inventoryAvailableRepository;
    private InventoryRepository inventoryRepository;
    private EventPublishingHelper eventPublishingHelper;
    private WmsInventoryReconciliationService service;

    private final UUID skuId = UUID.randomUUID();
    private final UUID inventoryId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private static final String SKU_CODE = "SKU-APPLE-001";

    @BeforeEach
    void setUp() {
        skuSnapshotRepository = mock(WmsSkuSnapshotRepository.class);
        inventoryAvailableRepository = mock(WmsInventoryAvailableRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        eventPublishingHelper = mock(EventPublishingHelper.class);
        service = new WmsInventoryReconciliationService(
                skuSnapshotRepository, inventoryAvailableRepository,
                inventoryRepository, eventPublishingHelper, FIXED);
    }

    private void givenResolvable(int currentStock) {
        given(skuSnapshotRepository.findById(skuId))
                .willReturn(Optional.of(WmsSkuSnapshotEntity.of(skuId, SKU_CODE, 0, Instant.now(FIXED))));
        given(inventoryRepository.findVariantBySku(SKU_CODE))
                .willReturn(Optional.of(new VariantRef(variantId, productId, currentStock)));
    }

    @Test
    @DisplayName("첫 관측은 baseline(delta 0) — 재고 변경/이벤트 없음, ledger 저장")
    void firstSight_establishesBaseline() {
        givenResolvable(50);
        given(inventoryAvailableRepository.findById(inventoryId)).willReturn(Optional.empty());

        service.reconcileAvailable(inventoryId, skuId, 100);

        verify(inventoryAvailableRepository).save(any(WmsInventoryAvailableEntity.class));
        verify(inventoryRepository, never()).save(any(Inventory.class));
        verifyNoInteractions(eventPublishingHelper);
    }

    @Test
    @DisplayName("두 번째 관측의 음수 delta는 재고를 차감하고 stock-changed(WMS_RECONCILIATION)를 발행")
    void secondSight_negativeDelta_reducesStockAndEmits() {
        givenResolvable(50);
        given(inventoryAvailableRepository.findById(inventoryId))
                .willReturn(Optional.of(WmsInventoryAvailableEntity.of(inventoryId, skuId, 100, Instant.now(FIXED))));

        service.reconcileAvailable(inventoryId, skuId, 95); // delta -5

        verify(inventoryRepository).save(argThatStockIs(45));
        verify(eventPublishingHelper).publishSafely(any(ProductEvent.class), eq("variant(wms-reconciliation)"), eq(variantId));
    }

    @Test
    @DisplayName("양수 delta는 재고를 증가")
    void positiveDelta_increasesStock() {
        givenResolvable(50);
        given(inventoryAvailableRepository.findById(inventoryId))
                .willReturn(Optional.of(WmsInventoryAvailableEntity.of(inventoryId, skuId, 100, Instant.now(FIXED))));

        service.reconcileAvailable(inventoryId, skuId, 130); // delta +30

        verify(inventoryRepository).save(argThatStockIs(80));
    }

    @Test
    @DisplayName("underflow는 0으로 clamp")
    void underflow_clampsAtZero() {
        givenResolvable(3);
        given(inventoryAvailableRepository.findById(inventoryId))
                .willReturn(Optional.of(WmsInventoryAvailableEntity.of(inventoryId, skuId, 100, Instant.now(FIXED))));

        service.reconcileAvailable(inventoryId, skuId, 90); // delta -10, stock 3 -> clamp 0

        verify(inventoryRepository).save(argThatStockIs(0));
    }

    @Test
    @DisplayName("delta 0(같은 availableQty 재전달)은 재고 변경/이벤트 없음")
    void zeroDelta_noStockChange() {
        givenResolvable(50);
        given(inventoryAvailableRepository.findById(inventoryId))
                .willReturn(Optional.of(WmsInventoryAvailableEntity.of(inventoryId, skuId, 100, Instant.now(FIXED))));

        service.reconcileAvailable(inventoryId, skuId, 100); // delta 0

        verify(inventoryRepository, never()).save(any(Inventory.class));
        verifyNoInteractions(eventPublishingHelper);
    }

    @Test
    @DisplayName("skuCode 스냅샷 없으면 skip(재고/ledger 무변경)")
    void noSnapshot_skips() {
        given(skuSnapshotRepository.findById(skuId)).willReturn(Optional.empty());

        service.reconcileAvailable(inventoryId, skuId, 100);

        verify(inventoryAvailableRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("skuCode가 매핑되는 variant 없으면 skip")
    void noVariant_skips() {
        given(skuSnapshotRepository.findById(skuId))
                .willReturn(Optional.of(WmsSkuSnapshotEntity.of(skuId, SKU_CODE, 0, Instant.now(FIXED))));
        given(inventoryRepository.findVariantBySku(SKU_CODE)).willReturn(Optional.empty());

        service.reconcileAvailable(inventoryId, skuId, 100);

        verify(inventoryAvailableRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("master.sku 신규는 스냅샷 insert")
    void upsertSkuSnapshot_inserts() {
        given(skuSnapshotRepository.findById(skuId)).willReturn(Optional.empty());

        service.upsertSkuSnapshot(skuId, SKU_CODE, 0);

        verify(skuSnapshotRepository).save(any(WmsSkuSnapshotEntity.class));
    }

    @Test
    @DisplayName("master.sku 과거 version은 무시(out-of-order)")
    void upsertSkuSnapshot_staleVersionIgnored() {
        WmsSkuSnapshotEntity existing = WmsSkuSnapshotEntity.of(skuId, SKU_CODE, 5, Instant.now(FIXED));
        given(skuSnapshotRepository.findById(skuId)).willReturn(Optional.of(existing));

        service.upsertSkuSnapshot(skuId, "SKU-OLD", 3); // stale

        verify(skuSnapshotRepository, never()).save(any());
        assertThat(existing.getSkuCode()).isEqualTo(SKU_CODE); // unchanged
    }

    private static Inventory argThatStockIs(int expected) {
        return org.mockito.ArgumentMatchers.argThat(inv ->
                inv != null && inv.currentStock().value() == expected);
    }
}
