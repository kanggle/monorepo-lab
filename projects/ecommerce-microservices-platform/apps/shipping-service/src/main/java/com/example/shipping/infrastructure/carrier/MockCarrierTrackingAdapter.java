package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.port.CarrierTrackingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default (no real I/O) carrier adapter (TASK-BE-293). Active when
 * {@code shipping.carrier.mode=mock} (the default). Returns the configured
 * {@code shipping.carrier.mock-status} verbatim, or {@link Optional#empty()} when it
 * is blank (the default) — so out of the box the carrier integration is OFF
 * (net-zero: a refresh is a no-op, the v1 admin-driven baseline is unchanged). Set a
 * mock-status (e.g. {@code IN_TRANSIT}) to exercise the refresh in dev, or switch to
 * {@code mode=http} for a real provider. Mutually exclusive with
 * {@link HttpCarrierTrackingAdapter} — exactly one {@link CarrierTrackingPort} bean.
 */
@Component
@ConditionalOnProperty(name = "shipping.carrier.mode", havingValue = "mock", matchIfMissing = true)
public class MockCarrierTrackingAdapter implements CarrierTrackingPort {

    private final String mockStatus;

    public MockCarrierTrackingAdapter(
            @Value("${shipping.carrier.mock-status:}") String mockStatus) {
        this.mockStatus = mockStatus;
    }

    @Override
    public Optional<CarrierTrackingSnapshot> fetchLatest(String carrier, String trackingNumber) {
        if (mockStatus == null || mockStatus.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CarrierTrackingSnapshot(mockStatus));
    }
}
