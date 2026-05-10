package com.wms.outbound.adapter.out.tms;

import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.domain.model.Shipment;

/**
 * Translation between the {@link Shipment} domain aggregate and the
 * vendor-shaped {@link TmsShipmentRequest} / {@link TmsShipmentResponse}
 * records (per {@code integration-heavy} I8).
 *
 * <p>Package-private — only {@link TmsClientAdapter} consumes this mapper.
 * If the vendor changes its schema, only this class and the two DTO records
 * need to update; the application / domain layer is insulated.
 */
final class TmsShipmentMapper {

    private TmsShipmentMapper() {
    }

    static TmsShipmentRequest toRequest(Shipment shipment) {
        return new TmsShipmentRequest(
                shipment.getId(),
                shipment.getShipmentNo(),
                shipment.getCarrierCode(),
                shipment.getShippedAt(),
                shipment.getOrderId());
    }

    /**
     * Translates the vendor response into the domain-facing
     * {@link TmsAcknowledgement}.
     *
     * <p>Per {@code external-integrations.md} §2.9 the vendor's
     * {@code status} field drives the {@code success} flag:
     * {@code ACCEPTED} / {@code PENDING_CARRIER_ASSIGNMENT} → success;
     * {@code REJECTED} → not-success (caller transitions
     * {@code Shipment.tms_status} to {@code NOTIFY_FAILED}).
     */
    static TmsAcknowledgement toAcknowledgement(TmsShipmentResponse response) {
        boolean success = isSuccess(response.status());
        return new TmsAcknowledgement(
                success,
                response.tmsRequestId(),
                response.trackingNumber(),
                response.carrierCode());
    }

    private static boolean isSuccess(String vendorStatus) {
        if (vendorStatus == null) {
            // Treat unknown / missing status as success — vendor returned 2xx.
            return true;
        }
        return switch (vendorStatus.toUpperCase()) {
            case "ACCEPTED", "PENDING_CARRIER_ASSIGNMENT" -> true;
            case "REJECTED" -> false;
            default -> true;
        };
    }
}
