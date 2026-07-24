package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vendor-shaped EasyPost {@code POST /shipments} response (I8) — <b>package-private</b>. The
 * mapper translates it into the domain-facing {@code DispatchAck}; the vendor field names
 * ({@code tracking_code}, {@code selected_rate.carrier}) never leak past the adapter. Unknown
 * fields are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record EasyPostShipmentResponse(
        @JsonProperty("id") String id,
        @JsonProperty("tracking_code") String trackingCode,
        @JsonProperty("selected_rate") SelectedRate selectedRate,
        @JsonProperty("status") String status) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SelectedRate(@JsonProperty("carrier") String carrier) {
    }

    String carrier() {
        return selectedRate == null ? null : selectedRate.carrier();
    }
}
