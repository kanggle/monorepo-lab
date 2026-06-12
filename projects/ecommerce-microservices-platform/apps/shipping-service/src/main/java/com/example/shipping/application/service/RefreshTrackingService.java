package com.example.shipping.application.service;

import com.example.shipping.application.port.CarrierTrackingPort;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.domain.exception.ShippingNotFoundException;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import com.example.shipping.application.port.ShippingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Refresh a shipment's status from its carrier (TASK-BE-293, the v2 carrier
 * integration first increment). Admin-triggered: given a shipment that already
 * carries a tracking number + carrier, fetch the carrier's latest status and advance
 * the shipment forward to it. The carrier-driven {@code auto-collect} scheduler is a
 * later increment; this exercises the integration deterministically.
 *
 * <p><b>Best-effort.</b> The carrier port never throws — a carrier outage / unknown
 * status returns empty and the refresh is a no-op (the shipment is unchanged, no
 * event). <b>Forward-only.</b> A carrier status at or behind the current status is a
 * no-op (shipments never regress). A carrier status ahead is reached by advancing
 * through the linear {@code PREPARING → SHIPPED → IN_TRANSIT → DELIVERED} chain one
 * valid step at a time (each step appends domain status history); one consolidated
 * {@code original → final} event is published on a net change, matching the manual
 * transition's event contract.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTrackingService {

    private final ShippingRepository shippingRepository;
    private final ShippingEventPublisher shippingEventPublisher;
    private final CarrierTrackingPort carrierTrackingPort;
    private final Clock clock;

    @Transactional
    public UpdateShippingStatusResult refreshFromCarrier(String shippingId, String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole)) {
            throw new AccessDeniedException("Admin role required");
        }
        Shipping shipping = shippingRepository.findById(shippingId)
                .orElseThrow(() -> new ShippingNotFoundException(shippingId));

        String trackingNumber = shipping.getTrackingNumber();
        String carrier = shipping.getCarrier();
        if (trackingNumber == null || trackingNumber.isBlank()
                || carrier == null || carrier.isBlank()) {
            log.info("Shipping {} has no carrier/tracking yet; carrier refresh no-op", shippingId);
            return result(shipping);
        }

        Optional<ShippingStatus> target = carrierTrackingPort.fetchLatest(carrier, trackingNumber)
                .flatMap(snapshot -> CarrierStatusMapper.toShippingStatus(snapshot.rawStatus()));
        if (target.isEmpty()) {
            log.info("Carrier returned no usable status for shipping {} ({}/{}); refresh no-op",
                    shippingId, carrier, trackingNumber);
            return result(shipping);
        }

        ShippingStatus original = shipping.getStatus();
        ShippingStatus goal = target.get();
        ShippingStatus[] chain = ShippingStatus.values();
        ShippingStatus current = original;
        while (current.ordinal() < goal.ordinal()) {
            ShippingStatus next = chain[current.ordinal() + 1];
            shipping.transitionTo(next, trackingNumber, carrier, clock);
            current = next;
        }

        if (current == original) {
            log.info("Carrier status {} for shipping {} is not ahead of {}; refresh no-op",
                    goal, shippingId, original);
            return result(shipping);
        }

        Shipping saved = shippingRepository.save(shipping);
        shippingEventPublisher.publishShippingStatusChanged(
                saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                original, saved.getStatus(), saved.getTrackingNumber(), saved.getCarrier());
        log.info("Shipping {} advanced {} -> {} via carrier refresh", shippingId, original, saved.getStatus());
        return new UpdateShippingStatusResult(saved.getShippingId(), saved.getStatus(), saved.getUpdatedAt());
    }

    private static UpdateShippingStatusResult result(Shipping shipping) {
        return new UpdateShippingStatusResult(
                shipping.getShippingId(), shipping.getStatus(), shipping.getUpdatedAt());
    }
}
