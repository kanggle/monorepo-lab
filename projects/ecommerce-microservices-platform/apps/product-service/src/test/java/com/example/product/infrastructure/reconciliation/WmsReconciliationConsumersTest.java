package com.example.product.infrastructure.reconciliation;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.InventoryAdjustedMessage;
import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.InventoryReceivedMessage;
import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.MasterSkuMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("wms reconciliation consumers 단위 테스트 (ADR-MONO-022 §D4 v2(b), ADR-MONO-058 D7)")
class WmsReconciliationConsumersTest {

    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String SKU_ID = UUID.randomUUID().toString();
    private static final String INVENTORY_ID = UUID.randomUUID().toString();

    /** Answers {@code EventDedupePort#process} by running the supplied work and returning APPLIED. */
    private static EventDedupePort.Outcome runWork(org.mockito.invocation.InvocationOnMock invocation) {
        ((Runnable) invocation.getArgument(2)).run();
        return EventDedupePort.Outcome.APPLIED;
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class MasterSku {
        @Mock WmsInventoryReconciliationService service;
        @Mock EventDedupePort wmsReconciliationDedupe;
        @Mock ObjectMapper objectMapper;
        @InjectMocks WmsMasterSkuConsumer consumer;

        @BeforeEach
        void stubDedupeAppliesByDefault() {
            lenient().when(wmsReconciliationDedupe.process(any(), eq("wms.master.sku"), any()))
                    .thenAnswer(WmsReconciliationConsumersTest::runWork);
        }

        private MasterSkuMessage event(String skuId, String skuCode, long version) {
            return new MasterSkuMessage(EVENT_ID, "master.sku.created",
                    new MasterSkuMessage.MasterSkuPayload(new MasterSkuMessage.Sku(skuId, skuCode, version)));
        }

        @Test
        @DisplayName("정상 master.sku는 upsertSkuSnapshot으로 위임")
        void valid_delegates() {
            consumer.handle(event(SKU_ID, "SKU-APPLE-001", 2));
            verify(service).upsertSkuSnapshot(UUID.fromString(SKU_ID), "SKU-APPLE-001", 2);
        }

        @Test
        @DisplayName("중복은 skip")
        void duplicate_skips() {
            given(wmsReconciliationDedupe.process(any(), eq("wms.master.sku"), any()))
                    .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
            consumer.handle(event(SKU_ID, "SKU-APPLE-001", 2));
            verify(service, never()).upsertSkuSnapshot(any(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("null payload는 skip")
        void nullPayload_skips() {
            consumer.handle(new MasterSkuMessage(EVENT_ID, "master.sku.created", null));
            verify(service, never()).upsertSkuSnapshot(any(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class Inventory {
        @Mock WmsInventoryReconciliationService service;
        @Mock EventDedupePort wmsReconciliationDedupe;
        @Mock ObjectMapper objectMapper;
        @InjectMocks WmsInventoryReconciliationConsumer consumer;

        @BeforeEach
        void stubDedupeAppliesByDefault() {
            lenient().when(wmsReconciliationDedupe.process(any(), eq("wms.inventory.received"), any()))
                    .thenAnswer(WmsReconciliationConsumersTest::runWork);
            lenient().when(wmsReconciliationDedupe.process(any(), eq("wms.inventory.adjusted"), any()))
                    .thenAnswer(WmsReconciliationConsumersTest::runWork);
        }

        @Test
        @DisplayName("inventory.received 각 라인은 availableQtyAfter로 reconcile")
        void received_reconcilesPerLine() {
            var event = new InventoryReceivedMessage(EVENT_ID, "inventory.received",
                    new InventoryReceivedMessage.ReceivedPayload(List.of(
                            new InventoryReceivedMessage.ReceivedLine(INVENTORY_ID, SKU_ID, 120))));

            consumer.handleReceived(event);

            verify(service).reconcileAvailable(UUID.fromString(INVENTORY_ID), UUID.fromString(SKU_ID), 120);
        }

        @Test
        @DisplayName("inventory.adjusted는 inventory.availableQty로 reconcile")
        void adjusted_reconcilesWithSnapshot() {
            var event = new InventoryAdjustedMessage(EVENT_ID, "inventory.adjusted",
                    new InventoryAdjustedMessage.AdjustedPayload(INVENTORY_ID, SKU_ID,
                            new InventoryAdjustedMessage.InventorySnapshot(75)));

            consumer.handleAdjusted(event);

            verify(service).reconcileAvailable(UUID.fromString(INVENTORY_ID), UUID.fromString(SKU_ID), 75);
        }

        @Test
        @DisplayName("중복 inventory 이벤트는 skip")
        void duplicate_skips() {
            given(wmsReconciliationDedupe.process(any(), eq("wms.inventory.adjusted"), any()))
                    .willReturn(EventDedupePort.Outcome.IGNORED_DUPLICATE);
            var event = new InventoryAdjustedMessage(EVENT_ID, "inventory.adjusted",
                    new InventoryAdjustedMessage.AdjustedPayload(INVENTORY_ID, SKU_ID,
                            new InventoryAdjustedMessage.InventorySnapshot(75)));

            consumer.handleAdjusted(event);

            verify(service, never()).reconcileAvailable(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("null payload(received)는 skip")
        void nullPayload_skips() {
            consumer.handleReceived(new InventoryReceivedMessage(EVENT_ID, "inventory.received", null));
            verify(service, never()).reconcileAvailable(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        }
    }
}
