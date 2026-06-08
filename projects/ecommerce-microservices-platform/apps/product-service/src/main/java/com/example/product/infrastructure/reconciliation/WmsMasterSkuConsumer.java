package com.example.product.infrastructure.reconciliation;

import com.example.product.infrastructure.reconciliation.WmsReconciliationMessages.MasterSkuMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reverse-identity stream consumer (ADR-MONO-022 §D4 v2(b)): builds the local
 * {@code skuId → skuCode} snapshot from {@code wms.master.sku.v1} that the inventory
 * reconciliation needs (wms inventory events carry only {@code skuId}).
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class WmsMasterSkuConsumer {

    private final WmsInventoryReconciliationService reconciliationService;
    private final WmsReconciliationDedupe dedupe;
    private final ObjectMapper objectMapper;

    @Transactional
    @KafkaListener(topics = "wms.master.sku.v1", groupId = "product-service-wms")
    public void onMessage(@Payload String payload) throws JsonProcessingException {
        handle(objectMapper.readValue(payload, MasterSkuMessage.class));
    }

    void handle(MasterSkuMessage event) {
        UUID eventId = Reconciliations.parseUuidOrNull(event.eventId());
        if (dedupe.isDuplicate(eventId, "wms.master.sku")) {
            return;
        }
        if (event.payload() == null || event.payload().sku() == null) {
            log.warn("wms master.sku event has null payload/sku, skipping. eventId={}", event.eventId());
            return;
        }
        var sku = event.payload().sku();
        UUID skuId = Reconciliations.parseUuidOrNull(sku.id());
        if (skuId == null) {
            log.warn("wms master.sku event has no sku.id, skipping. eventId={}", event.eventId());
            return;
        }
        reconciliationService.upsertSkuSnapshot(skuId, sku.skuCode(), sku.version());
    }
}
