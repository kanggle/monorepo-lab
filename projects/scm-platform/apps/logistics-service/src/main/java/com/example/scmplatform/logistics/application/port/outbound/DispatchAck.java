package com.example.scmplatform.logistics.application.port.outbound;

import com.example.scmplatform.logistics.domain.model.Carrier;

/**
 * Vendor-neutral dispatch acknowledgement returned by {@link ShipmentDispatchPort}. The
 * vendor's own DTO shape never crosses the port (I8) — the adapter translates its response
 * into this domain-facing value (tracking number + carrier + which vendor answered).
 */
public record DispatchAck(String trackingNo, String carrierCode, Carrier vendor) {
}
