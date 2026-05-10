package com.wms.outbound.application.port.out;

/**
 * Internal model translation of a TMS push acknowledgement
 * (per integration-heavy I8). Independent of any TMS vendor wire format.
 *
 * @param success      {@code true} when the vendor accepted the shipment
 *                     ({@code ACCEPTED} / {@code PENDING_CARRIER_ASSIGNMENT}
 *                     statuses per {@code external-integrations.md} §2.9)
 * @param requestId    vendor's tracking handle for this push (echoed back as
 *                     {@code Shipment.tms_request_id})
 * @param trackingNo   carrier tracking number (may be null on
 *                     {@code PENDING_CARRIER_ASSIGNMENT}); future enhancement
 *                     can persist into {@code Shipment.tracking_no}
 * @param carrierCode  carrier code assigned by TMS (may be null on
 *                     {@code PENDING_CARRIER_ASSIGNMENT}); see
 *                     {@code external-integrations.md} §2.9 mapping table
 */
public record TmsAcknowledgement(
        boolean success,
        String requestId,
        String trackingNo,
        String carrierCode) {

    /**
     * Compact factory for tests / legacy callers that only need to convey
     * success + vendor request id.
     */
    public static TmsAcknowledgement success(String requestId) {
        return new TmsAcknowledgement(true, requestId, null, null);
    }
}
