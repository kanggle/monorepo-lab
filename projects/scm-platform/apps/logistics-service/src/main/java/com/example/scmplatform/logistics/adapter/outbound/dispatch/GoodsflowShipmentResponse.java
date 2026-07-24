package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Vendor-shaped 굿스플로 {@code POST /shipments} response (I8) — <b>package-private</b>. The
 * mapper translates it into the domain-facing {@code DispatchAck}; the vendor field names
 * ({@code invoiceNo} = 운송장번호, {@code deliveryCompanyCode} = the selected domestic carrier)
 * never leak past the adapter. Unknown fields are ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record GoodsflowShipmentResponse(
        @JsonProperty("id") String id,
        @JsonProperty("invoiceNo") String invoiceNo,
        @JsonProperty("deliveryCompanyCode") String deliveryCompanyCode,
        @JsonProperty("status") String status) {

    /** 운송장번호 (waybill / invoice number) → domain trackingNo. */
    String trackingCode() {
        return invoiceNo;
    }

    /** Selected domestic carrier code (CJ대한통운 / 한진 / …) → domain carrierCode. */
    String carrier() {
        return deliveryCompanyCode;
    }
}
