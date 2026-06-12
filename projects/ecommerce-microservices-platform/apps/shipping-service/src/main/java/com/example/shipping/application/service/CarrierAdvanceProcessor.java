package com.example.shipping.application.service;

import com.example.shipping.application.port.CarrierTrackingPort;
import com.example.shipping.application.port.CarrierTrackingPort.CarrierTrackingSnapshot;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * The shared carrier-driven forward-advance for one shipment, extracted from
 * {@link RefreshTrackingService} (TASK-BE-360) so the admin pull and the unattended
 * auto-collect sweep ({@link AutoCollectTrackingService}) reach a carrier-reported status the
 * <b>same way</b>: {@link CarrierTrackingPort#fetchLatest} → {@link CarrierStatusMapper} →
 * {@link ShippingForwardAdvancer} (forward-only) → on a net change save + publish one
 * consolidated {@code ShippingStatusChanged}; an unmapped non-blank status is made observable
 * via {@link CarrierStatusObserver}.
 *
 * <p><b>Best-effort / forward-only / net-zero</b> — identical to the v1 admin refresh:
 * <ul>
 *   <li>carrier outage / unavailable ({@link Optional#empty()}) → no-op, no event;</li>
 *   <li>unmapped non-blank status → no-op, no event, {@code carrier_status_unmapped} counted;</li>
 *   <li>status at/behind current → no-op (shipments never regress);</li>
 *   <li>status ahead → advance through the linear chain + publish {@code original → final}.</li>
 * </ul>
 * The caller supplies the {@code source} tag ({@code refresh} pull / {@code auto-collect} sweep)
 * for the unmapped counter. This component does not load the shipment, check tracking/carrier
 * presence, or authorise — those stay with each caller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CarrierAdvanceProcessor {

    private final ShippingRepository shippingRepository;
    private final ShippingEventPublisher shippingEventPublisher;
    private final CarrierTrackingPort carrierTrackingPort;
    private final CarrierStatusObserver carrierStatusObserver;
    private final Clock clock;

    /** Outcome of a single shipment's carrier-driven advance attempt. */
    public enum Outcome {
        /** Net change: advanced forward and a {@code ShippingStatusChanged} was published. */
        ADVANCED,
        /** No usable / not-ahead carrier status: shipment unchanged, no event. */
        NO_OP
    }

    /**
     * Run the carrier-driven forward-advance for {@code shipping}, which the caller has already
     * loaded and confirmed carries a non-blank tracking number + carrier. Returns the original
     * status when a net change was published, else {@link Optional#empty()}.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so the unattended sweep
     * ({@link AutoCollectTrackingService}) isolates each shipment: one item's rollback never
     * poisons the batch (AC-4). The admin refresh ({@link RefreshTrackingService}) calls this
     * inside its own request transaction; the nested commit is behaviourally identical
     * (the advance + event still persist atomically per shipment).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome advanceFromCarrier(Shipping shipping, String source) {
        String shippingId = shipping.getShippingId();
        String trackingNumber = shipping.getTrackingNumber();
        String carrier = shipping.getCarrier();

        Optional<CarrierTrackingSnapshot> snapshot = carrierTrackingPort.fetchLatest(carrier, trackingNumber);
        Optional<ShippingStatus> target = snapshot
                .flatMap(s -> CarrierStatusMapper.toShippingStatus(s.rawStatus()));
        if (target.isEmpty()) {
            // A non-blank aggregator status that did not map = an unmapped code (silent-stall
            // risk → make it observable); a blank/absent status carries no signal (net-zero).
            snapshot.ifPresent(s -> carrierStatusObserver.recordUnmapped(source, s.rawStatus()));
            log.info("Carrier returned no usable status for shipping {} ({}/{}); {} no-op",
                    shippingId, carrier, trackingNumber, source);
            return Outcome.NO_OP;
        }

        Optional<ShippingStatus> changedFrom =
                ShippingForwardAdvancer.advanceForward(shipping, target.get(), clock);
        if (changedFrom.isEmpty()) {
            log.info("Carrier status {} for shipping {} is not ahead of {}; {} no-op",
                    target.get(), shippingId, shipping.getStatus(), source);
            return Outcome.NO_OP;
        }

        ShippingStatus original = changedFrom.get();
        Shipping saved = shippingRepository.save(shipping);
        shippingEventPublisher.publishShippingStatusChanged(
                saved.getShippingId(), saved.getOrderId(), saved.getUserId(),
                original, saved.getStatus(), saved.getTrackingNumber(), saved.getCarrier());
        log.info("Shipping {} advanced {} -> {} via carrier {}", shippingId, original, saved.getStatus(), source);
        return Outcome.ADVANCED;
    }
}
