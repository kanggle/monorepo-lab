package com.wms.outbound.adapter.out.tms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Vendor-shaped response DTO from {@code POST {tms-base}/shipments}.
 *
 * <p>Per {@code integration-heavy} I8 this record is adapter-internal. The
 * adapter translates it to
 * {@link com.wms.outbound.application.port.out.TmsAcknowledgement} (the
 * domain-facing model) before returning across the port boundary.
 *
 * <p>Field shape mirrors the indicative TMS contract. The vendor's full
 * payload includes additional fields (operational metadata, carrier
 * details) that we deliberately drop — the mapper preserves only what the
 * domain needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record TmsShipmentResponse(
        String tmsRequestId,
        String trackingNumber,
        String carrierCode,
        String status,
        String message
) {
}
