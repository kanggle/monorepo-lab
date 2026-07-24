package com.example.scmplatform.logistics.application.usecase;

import java.util.UUID;

/**
 * Command for {@link ConsumeShippingConfirmedUseCase} — the consumed subset of the wms
 * {@code outbound.shipping.confirmed} event, already mapped off the camelCase envelope by the
 * inbound messaging adapter (the use case is framework- and Kafka-agnostic).
 *
 * @param eventId      envelope {@code eventId} — the T8 idempotency key (never null; validated at the adapter)
 * @param tenantId     envelope {@code tenantId} — echoed onto the dispatch record (nullable for B2B)
 * @param shipmentId   {@code payload.shipmentId} — dispatch identity + {@code shipment_id} dedup key (never null)
 * @param shipmentNo   {@code payload.shipmentNo} — operator-facing business identifier
 * @param orderId      {@code payload.orderId} — correlation only (nullable)
 * @param orderNo      {@code payload.orderNo} — correlation only (nullable)
 * @param carrierCode  {@code payload.carrierCode} — the {@code CarrierRouter} routing signal;
 *                     <b>nullable at source</b>, passed through raw (null → default vendor + degrade)
 */
public record ConsumeShippingConfirmedCommand(
        UUID eventId,
        String tenantId,
        UUID shipmentId,
        String shipmentNo,
        UUID orderId,
        String orderNo,
        String carrierCode
) {
}
