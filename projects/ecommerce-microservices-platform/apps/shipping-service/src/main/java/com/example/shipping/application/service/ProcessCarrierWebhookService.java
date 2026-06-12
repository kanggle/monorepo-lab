package com.example.shipping.application.service;

import com.example.shipping.application.command.CarrierWebhookCommand;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.port.WebhookDeliveryStore;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Ingests a carrier-pushed tracking webhook (TASK-BE-294, the inbound half of the
 * shipping-service carrier integration; the outbound pull is TASK-BE-293's
 * {@link RefreshTrackingService}). The carrier POSTs a delivery event that has already
 * been signature-verified by the inbound adapter; this applies it to the shipment.
 *
 * <p><b>Idempotent.</b> Carriers retry deliveries, so each is deduplicated by its
 * {@code deliveryId} (the dedup registration shares this method's transaction, so a failed
 * advance rolls it back and a retry can re-process). <b>Best-effort.</b> An unknown/unmapped
 * status, an unknown shipment, or a shipment without tracking/carrier yet is a no-op.
 * <b>Forward-only.</b> A status at or behind the current status is a no-op (shipments never
 * regress); a status ahead is reached via {@link ShippingForwardAdvancer}, publishing one
 * consolidated {@code original → final} {@code ShippingStatusChanged} event — the same
 * domain transition + event contract as the manual and refresh paths.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcessCarrierWebhookService {

    private final ShippingRepository shippingRepository;
    private final ShippingEventPublisher shippingEventPublisher;
    private final WebhookDeliveryStore webhookDeliveryStore;
    private final CarrierStatusObserver carrierStatusObserver;
    private final Clock clock;

    public enum WebhookOutcome { ADVANCED, IGNORED, DUPLICATE }

    @Transactional
    public WebhookOutcome ingest(CarrierWebhookCommand command) {
        if (!webhookDeliveryStore.registerIfFirst(command.deliveryId())) {
            log.info("Duplicate carrier webhook delivery {}; ignored", command.deliveryId());
            return WebhookOutcome.DUPLICATE;
        }

        Optional<ShippingStatus> target = CarrierStatusMapper.toShippingStatus(command.rawStatus());
        if (target.isEmpty()) {
            // Non-blank unmapped aggregator status = silent-stall risk → make it observable
            // (a blank status is a no-signal no-op; the observer ignores it = net-zero).
            carrierStatusObserver.recordUnmapped("webhook", command.rawStatus());
            log.info("Carrier webhook {} reported unmapped status '{}'; no-op",
                    command.deliveryId(), command.rawStatus());
            return WebhookOutcome.IGNORED;
        }

        Optional<Shipping> found = shippingRepository.findById(command.shippingId());
        if (found.isEmpty()) {
            log.info("Carrier webhook {} references unknown shipping {}; no-op",
                    command.deliveryId(), command.shippingId());
            return WebhookOutcome.IGNORED;
        }
        Shipping shipping = found.get();

        String trackingNumber = shipping.getTrackingNumber();
        String carrier = shipping.getCarrier();
        if (trackingNumber == null || trackingNumber.isBlank()
                || carrier == null || carrier.isBlank()) {
            log.info("Shipping {} has no carrier/tracking yet; carrier webhook no-op", command.shippingId());
            return WebhookOutcome.IGNORED;
        }

        Optional<ShippingStatus> changedFrom =
                ShippingForwardAdvancer.advanceForward(shipping, target.get(), clock);
        if (changedFrom.isEmpty()) {
            log.info("Carrier webhook status {} for shipping {} is not ahead of {}; no-op",
                    target.get(), command.shippingId(), shipping.getStatus());
            return WebhookOutcome.IGNORED;
        }

        ShippingStatus original = changedFrom.get();
        Shipping saved = shippingRepository.save(shipping);
        shippingEventPublisher.publishShippingStatusChanged(
                saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                original, saved.getStatus(), saved.getTrackingNumber(), saved.getCarrier());
        log.info("Shipping {} advanced {} -> {} via carrier webhook {}",
                command.shippingId(), original, saved.getStatus(), command.deliveryId());
        return WebhookOutcome.ADVANCED;
    }
}
