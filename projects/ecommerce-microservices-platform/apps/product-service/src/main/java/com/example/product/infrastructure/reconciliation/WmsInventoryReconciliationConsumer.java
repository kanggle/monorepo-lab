package com.example.product.infrastructure.reconciliation;

import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.InventoryAdjustedMessage;
import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.InventoryReceivedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Warehouse-origin inventory reconciliation consumer (ADR-MONO-022 §D4 v2(b), Option B).
 * Consumes ONLY {@code inventory.received} (restock) + {@code inventory.adjusted}
 * (damage/loss/manual) — the reservation lifecycle (reserved/released/confirmed) and
 * transferred are excluded by design (double-count / SKU-invariant). Dedupe on the wms
 * envelope {@code eventId}; each line/event applies an availableQty delta.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class WmsInventoryReconciliationConsumer {

    private final WmsInventoryReconciliationService reconciliationService;
    private final WmsReconciliationDedupe dedupe;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.inventory.received.v1", groupId = "product-service-wms")
    public void onReceived(@Payload String payload) throws JsonProcessingException {
        handleReceived(objectMapper.readValue(payload, InventoryReceivedMessage.class));
    }

    void handleReceived(InventoryReceivedMessage event) {
        if (dedupe.isDuplicate(Reconciliations.parseUuidOrNull(event.eventId()), "wms.inventory.received")) {
            return;
        }
        if (event.payload() == null || event.payload().lines() == null) {
            log.warn("wms inventory.received event has null payload/lines, skipping. eventId={}", event.eventId());
            return;
        }
        List<InventoryReceivedMessage.ReceivedLine> lines = event.payload().lines();
        for (InventoryReceivedMessage.ReceivedLine line : lines) {
            UUID inventoryId = Reconciliations.parseUuidOrNull(line.inventoryId());
            UUID skuId = Reconciliations.parseUuidOrNull(line.skuId());
            reconciliationService.reconcileAvailable(inventoryId, skuId, line.availableQtyAfter());
        }
    }

    @Transactional
    @KafkaListener(topics = "wms.inventory.adjusted.v1", groupId = "product-service-wms")
    public void onAdjusted(@Payload String payload) throws JsonProcessingException {
        handleAdjusted(objectMapper.readValue(payload, InventoryAdjustedMessage.class));
    }

    void handleAdjusted(InventoryAdjustedMessage event) {
        if (dedupe.isDuplicate(Reconciliations.parseUuidOrNull(event.eventId()), "wms.inventory.adjusted")) {
            return;
        }
        if (event.payload() == null || event.payload().inventory() == null) {
            log.warn("wms inventory.adjusted event has null payload/inventory, skipping. eventId={}", event.eventId());
            return;
        }
        UUID inventoryId = Reconciliations.parseUuidOrNull(event.payload().inventoryId());
        UUID skuId = Reconciliations.parseUuidOrNull(event.payload().skuId());
        reconciliationService.reconcileAvailable(inventoryId, skuId, event.payload().inventory().availableQty());
    }
}
