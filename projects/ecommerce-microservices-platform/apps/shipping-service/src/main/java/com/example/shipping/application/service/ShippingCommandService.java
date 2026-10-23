package com.example.shipping.application.service;

import com.example.shipping.application.command.CancelShippingCommand;
import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.command.UpdateShippingStatusCommand;
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
import java.time.Instant;

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
     * Marks the Shipping for the given order as routed through the wms warehouse
     * (ADR-MONO-022 D4 v2(c)) — invoked by the OrderConfirmed consumer in the SAME
     * transaction, immediately after the forward-leg fulfillment-intent event is
     * actually published. Idempotent: an already-routed (or missing) row is a no-op.
     * Lets a later manual ship-confirm offer the operator the wms-deduct option.
     */
    @Transactional
    public void markShippingWmsRouted(String orderId) {
        shippingRepository.findByOrderId(orderId).ifPresentOrElse(
                shipping -> {
                    if (!shipping.isWmsRouted()) {
                        shipping.markWmsRouted();
                        shippingRepository.save(shipping);
                        log.info("Shipping marked wmsRouted: orderId={}", orderId);
                    }
                },
                () -> log.warn("markShippingWmsRouted: no shipping for orderId={}, skipping", orderId));
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

        if (shipping.getStatus() == ShippingStatus.CANCELLED) {
            // TASK-BE-649 (D4 v2(d), Y-A): the order was cancelled and refunded, but wms shipped
            // anyway — it refused the cancel after pick (X-A), or ship-confirm raced it. Never
            // resurrect the shipment; this is the return flow and needs a human.
            log.error("ALERT wms shipped an order whose shipping is CANCELLED — return flow needed. "
                            + "shippingId={}, orderId={}, tracking={}, carrier={}",
                    shipping.getShippingId(), orderId, trackingNumber, carrier);
            return;
        }

        if (shipping.getStatus() == ShippingStatus.SHIPPED
                || shipping.getStatus() == ShippingStatus.IN_TRANSIT
                || shipping.getStatus() == ShippingStatus.DELIVERED) {
            log.info("Shipping for orderId={} already past PREPARING (status={}), skipping (idempotent)",
                    orderId, shipping.getStatus());
            return;
        }

        ShippingStatus previousStatus = shipping.transitionTo(
                ShippingStatus.SHIPPED, trackingNumber, carrier, clock);
        Shipping saved = saveAndPublishStatusChange(shipping, previousStatus);

        log.info("Shipping marked SHIPPED via wms confirmation: shippingId={}, orderId={}, tracking={}, carrier={}",
                saved.getShippingId(), orderId, trackingNumber, carrier);
    }

    /**
     * Pre-dispatch cancel on a consumed {@code OrderCancelled} (ADR-MONO-022 D4 v2(d), X-A /
     * Y-A, TASK-BE-649). Located by {@code orderId} (tenant-agnostic system path; the row keeps
     * its own tenant). System-driven, so no operator role check.
     *
     * <ul>
     *   <li>{@code PREPARING} → {@code CANCELLED}. When the row is {@code wmsRouted}, the
     *       {@code ecommerce.fulfillment.cancel-requested.v1} outbox row is written in this
     *       same transaction, so wms is asked to cancel exactly when the shipment is.</li>
     *   <li>{@code SHIPPED} / {@code IN_TRANSIT} / {@code DELIVERED} → logged no-op (the goods
     *       have left: return flow, not a cancel).</li>
     *   <li>already {@code CANCELLED} / no row → no-op (redelivery; cancel before confirm).</li>
     * </ul>
     *
     * <p>No {@code ShippingStatusChanged} is published for {@code CANCELLED}: order-service
     * ignores it and notification-service would send the customer an unapproved shipping notice
     * (shipping-events.md § ShippingStatusChanged).
     */
    @Transactional
    public void cancelByOrderId(CancelShippingCommand command) {
        String orderId = command.orderId();
        Shipping shipping = shippingRepository.findByOrderId(orderId).orElse(null);
        if (shipping == null) {
            log.info("cancelByOrderId: no shipping for orderId={} (cancelled before confirm?), skipping",
                    orderId);
            return;
        }

        ShippingStatus current = shipping.getStatus();
        if (current == ShippingStatus.CANCELLED) {
            log.info("Shipping for orderId={} already CANCELLED, skipping (idempotent)", orderId);
            return;
        }
        if (!current.canTransitionTo(ShippingStatus.CANCELLED)) {
            log.warn("Order cancelled after dispatch — shipping not cancelled (return flow). "
                    + "shippingId={}, orderId={}, status={}", shipping.getShippingId(), orderId, current);
            return;
        }

        shipping.cancel(clock);
        Shipping saved = shippingRepository.save(shipping);

        if (saved.isWmsRouted()) {
            Instant requestedAt = command.cancelledAt() != null ? command.cancelledAt() : Instant.now(clock);
            shippingEventPublisher.publishFulfillmentCancelRequested(
                    saved.getTenantId(), saved.getOrderId(), requestedAt);
            log.info("Shipping cancelled + wms cancel requested: shippingId={}, orderId={}",
                    saved.getShippingId(), orderId);
        } else {
            log.info("Shipping cancelled (not wms-routed, nothing to ask wms): shippingId={}, orderId={}",
                    saved.getShippingId(), orderId);
        }
    }

    @Transactional
    public UpdateShippingStatusResult updateStatus(UpdateShippingStatusCommand command) {
        OperatorRoleGuard.requireWrite(command.userRole());
        // Tenant-scoped lookup (admin mutation): a cross-tenant shippingId → 404 (M3).
        Shipping shipping = shippingRepository.findByIdForTenant(command.shippingId())
                .orElseThrow(() -> new ShippingNotFoundException(command.shippingId()));

        ShippingStatus previousStatus = shipping.transitionTo(
                command.status(), command.trackingNumber(), command.carrier(), clock);
        Shipping saved = saveAndPublishStatusChange(shipping, previousStatus);

        // ADR-MONO-022 D4 v2(c): operator-elected wms inventory deduction for a
        // hand-shipped, warehouse-routed order. Only when the target status is SHIPPED,
        // the operator opted in (deductWmsInventory=true), AND the order was actually
        // forward-leg fulfillment-published (wmsRouted) — otherwise no-op (existing behaviour).
        if (saved.getStatus() == ShippingStatus.SHIPPED
                && command.deductWmsInventory()
                && saved.isWmsRouted()) {
            shippingEventPublisher.publishManualShipConfirmRequested(
                    saved.getTenantId(), saved.getOrderId(),
                    saved.getCarrier(), saved.getTrackingNumber());
            log.info("Manual ship-confirm requested (wms deduct): shippingId={}, orderId={}",
                    saved.getShippingId(), saved.getOrderId());
        }

        log.info("Shipping status updated: shippingId={}, {} -> {}",
                saved.getShippingId(), previousStatus, saved.getStatus());

        return new UpdateShippingStatusResult(
                saved.getShippingId(), saved.getStatus(), saved.getUpdatedAt());
    }

    /**
     * Persist the transitioned aggregate and publish the resulting status-changed event — the
     * save+publish tail shared by the wms-confirmation and admin-update paths. Runs in the
     * caller's transaction (private method); save → publish order is unchanged.
     */
    private Shipping saveAndPublishStatusChange(Shipping shipping, ShippingStatus previousStatus) {
        Shipping saved = shippingRepository.save(shipping);
        shippingEventPublisher.publishShippingStatusChanged(
                saved.getTenantId(), saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                previousStatus, saved.getStatus(),
                saved.getTrackingNumber(), saved.getCarrier());
        return saved;
    }

}
