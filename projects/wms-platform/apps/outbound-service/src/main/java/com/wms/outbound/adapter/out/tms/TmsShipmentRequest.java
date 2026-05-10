package com.wms.outbound.adapter.out.tms;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Vendor-shaped request DTO sent to {@code POST {tms-base}/shipments}.
 *
 * <p>Per {@code integration-heavy} I8 this record is adapter-internal —
 * never leaks into the application or domain layer. The mapping
 * {@code Shipment → TmsShipmentRequest} is performed in
 * {@link TmsShipmentMapper}.
 *
 * <p>Field shape mirrors the indicative TMS contract (see
 * {@code specs/contracts/http/tms-shipment-api.md} — Open Item; vendor
 * surface). When the vendor finalises its schema, only this record + the
 * mapper change.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record TmsShipmentRequest(
        UUID shipmentId,
        String shipmentNo,
        String carrierCode,
        Instant shippedAt,
        UUID orderId
) {
}
