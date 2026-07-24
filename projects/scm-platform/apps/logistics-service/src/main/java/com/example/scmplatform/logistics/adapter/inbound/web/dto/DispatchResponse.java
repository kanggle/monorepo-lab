package com.example.scmplatform.logistics.adapter.inbound.web.dto;

import com.example.scmplatform.logistics.domain.model.Dispatch;

import java.time.Instant;
import java.util.UUID;

/**
 * Operator-facing view of a {@link Dispatch} — the {@code inspect} and {@code :retry} response
 * body. The vendor ack is surfaced as {@code trackingNo} + {@code carrierCode} + {@code vendor};
 * a {@code DISPATCH_FAILED} dispatch carries {@code failureReason}.
 */
public record DispatchResponse(
        UUID id,
        UUID shipmentId,
        String shipmentNo,
        UUID orderId,
        String orderNo,
        String status,
        String carrierCode,
        String trackingNo,
        String vendor,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static DispatchResponse from(Dispatch d) {
        return new DispatchResponse(
                d.getId(),
                d.getShipmentId().value(),
                d.getShipmentNo(),
                d.getOrderId(),
                d.getOrderNo(),
                d.getStatus().name(),
                d.getCarrierCode() == null ? null : d.getCarrierCode().value(),
                d.getTrackingNo() == null ? null : d.getTrackingNo().value(),
                d.getVendor() == null ? null : d.getVendor().name(),
                d.getFailureReason(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
