package com.example.shipping.application.service;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.command.UpdateShippingStatusCommand;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShippingCommandService {

    private final ShippingRepository shippingRepository;
    private final ShippingEventPublisher shippingEventPublisher;
    private final Clock clock;

    @Transactional
    public void createShipping(CreateShippingCommand command) {
        if (shippingRepository.existsByOrderId(command.orderId())) {
            log.info("Shipping already exists for orderId={}, skipping (idempotent)", command.orderId());
            return;
        }

        Shipping shipping = Shipping.create(command.tenantId(), command.orderId(), command.userId(), clock);
        shippingRepository.save(shipping);
        log.info("Shipping created: shippingId={}, orderId={}", shipping.getShippingId(), command.orderId());
    }

    /**
     * Return-leg transition (ADR-MONO-022 §D7): mark the Shipping for the given
     * order {@code SHIPPED} with the wms-assigned tracking/carrier. Located by
     * {@code orderId} (the wms {@code orderNo} correlation key). Idempotent: an
     * already-SHIPPED record is a no-op. System-driven, so no admin role check.
     *
     * @throws ShippingNotFoundException if no Shipping exists for the order
     *                                   (caller routes to DLT).
     */
    @Transactional
    public void markShippedByOrderId(String orderId, String trackingNumber, String carrier) {
        Shipping shipping = shippingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ShippingNotFoundException(orderId));

        if (shipping.getStatus() == ShippingStatus.SHIPPED
                || shipping.getStatus() == ShippingStatus.IN_TRANSIT
                || shipping.getStatus() == ShippingStatus.DELIVERED) {
            log.info("Shipping for orderId={} already past PREPARING (status={}), skipping (idempotent)",
                    orderId, shipping.getStatus());
            return;
        }

        ShippingStatus previousStatus = shipping.transitionTo(
                ShippingStatus.SHIPPED, trackingNumber, carrier, clock);

        Shipping saved = shippingRepository.save(shipping);

        shippingEventPublisher.publishShippingStatusChanged(
                saved.getTenantId(), saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                previousStatus, saved.getStatus(),
                saved.getTrackingNumber(), saved.getCarrier());

        log.info("Shipping marked SHIPPED via wms confirmation: shippingId={}, orderId={}, tracking={}, carrier={}",
                saved.getShippingId(), orderId, trackingNumber, carrier);
    }

    @Transactional
    public UpdateShippingStatusResult updateStatus(UpdateShippingStatusCommand command) {
        validateAdminRole(command.userRole());
        // Tenant-scoped lookup (admin mutation): a cross-tenant shippingId → 404 (M3).
        Shipping shipping = shippingRepository.findByIdForTenant(command.shippingId())
                .orElseThrow(() -> new ShippingNotFoundException(command.shippingId()));

        ShippingStatus previousStatus = shipping.transitionTo(
                command.status(), command.trackingNumber(), command.carrier(), clock);

        Shipping saved = shippingRepository.save(shipping);

        shippingEventPublisher.publishShippingStatusChanged(
                saved.getTenantId(), saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                previousStatus, saved.getStatus(),
                saved.getTrackingNumber(), saved.getCarrier());

        log.info("Shipping status updated: shippingId={}, {} -> {}",
                saved.getShippingId(), previousStatus, saved.getStatus());

        return new UpdateShippingStatusResult(
                saved.getShippingId(), saved.getStatus(), saved.getUpdatedAt());
    }

    private void validateAdminRole(String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
