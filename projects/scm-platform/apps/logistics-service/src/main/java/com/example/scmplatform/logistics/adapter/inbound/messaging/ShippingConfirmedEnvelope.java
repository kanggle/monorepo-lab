package com.example.scmplatform.logistics.adapter.inbound.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deserialization DTO for the wms {@code outbound.shipping.confirmed} event (the seam this
 * service consumes). The wms producer uses the <b>camelCase</b> envelope
 * {@code eventId}/{@code eventType}/{@code eventVersion}/{@code occurredAt}/{@code producer}/
 * {@code aggregateType}/{@code aggregateId}/{@code tenantId}/{@code payload} — <b>not</b> the scm
 * {@code BaseEventPublisher} shape (subscriptions contract § Envelope; wms {@code outbound-events.md}
 * § Global Envelope + §7).
 *
 * <p><b>Forward compatibility.</b> {@code @JsonIgnoreProperties(ignoreUnknown = true)} — the
 * consumer reads only the subset it needs and tolerates additive producer fields
 * ({@code FAIL_ON_UNKNOWN_PROPERTIES = false}), mirroring the sibling
 * demand-planning-service {@code WmsAlertEnvelope}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShippingConfirmedEnvelope(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("eventVersion") Integer eventVersion,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("producer") String producer,
        @JsonProperty("aggregateType") String aggregateType,
        @JsonProperty("aggregateId") String aggregateId,
        // Envelope-level echoed ecommerce tenant (ADR-MONO-022 facet d); null for B2B. logistics
        // does not interpret it — only stores it on the dispatch record for correlation.
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("payload") Payload payload
) {

    /**
     * Consumed payload subset ({@code outbound.shipping.confirmed}). Authoritative shape:
     * wms {@code outbound-events.md} §7. {@code carrierCode} is <b>nullable at source</b> ("may be
     * null if carrier was not specified at shipping time") — passed through raw as the
     * {@code CarrierRouter} routing signal (null → default vendor + {@code CARRIER_UNROUTABLE}
     * degrade; never coerced, never dropped).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            @JsonProperty("shipmentId") UUID shipmentId,
            @JsonProperty("shipmentNo") String shipmentNo,
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("orderNo") String orderNo,
            @JsonProperty("warehouseId") UUID warehouseId,
            @JsonProperty("carrierCode") String carrierCode,
            @JsonProperty("shippedAt") Instant shippedAt,
            @JsonProperty("lines") List<Line> lines
    ) {
    }

    /** Parcel-contents summary carried on the seam (not used to build the Phase-1 dispatch). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(
            @JsonProperty("orderLineId") UUID orderLineId,
            @JsonProperty("skuId") UUID skuId,
            @JsonProperty("lotId") UUID lotId,
            @JsonProperty("locationId") UUID locationId,
            @JsonProperty("qtyConfirmed") Integer qtyConfirmed
    ) {
    }

    /**
     * A malformed envelope is <b>non-retryable</b> → immediate DLT (subscriptions contract §
     * Retry + DLT). Required for idempotency + routing: the envelope {@code eventId} (the T8
     * dedup key), the {@code payload}, and the {@code payload.shipmentId} (the dispatch identity
     * + {@code shipment_id} dedup key). A null in any of these can never succeed on redelivery.
     */
    public boolean isValid() {
        return eventId != null
                && payload != null
                && payload.shipmentId() != null;
    }
}
