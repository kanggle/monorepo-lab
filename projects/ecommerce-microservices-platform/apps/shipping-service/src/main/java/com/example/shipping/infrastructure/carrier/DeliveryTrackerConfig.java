package com.example.shipping.infrastructure.carrier;

import com.example.shipping.application.port.CarrierTrackingPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the Delivery Tracker outbound pull stack (TASK-BE-364) ONLY when
 * {@code shipping.carrier.mode=delivery-tracker}. With the default {@code mode=mock} (or
 * {@code mode=http}) this configuration is inert, so {@link MockCarrierTrackingAdapter} /
 * {@link HttpCarrierTrackingAdapter} remain the single active {@link CarrierTrackingPort} bean
 * (net-zero, AC-6).
 *
 * <p>The token provider + adapter are plain (non-{@code @Component}) types constructed here so
 * they stay unit-testable against MockWebServer (mirroring {@link HttpCarrierTrackingAdapter}).
 * A blank credential leaves the adapter active-but-no-op (every fetch returns empty without an
 * outbound call), satisfying the "blank = disabled / net-zero" contract (AC-5) without a separate
 * bean condition.
 */
@Configuration
@ConditionalOnProperty(name = "shipping.carrier.mode", havingValue = "delivery-tracker")
@EnableConfigurationProperties(DeliveryTrackerProperties.class)
class DeliveryTrackerConfig {

    @Bean
    DeliveryTrackerTokenProvider deliveryTrackerTokenProvider(
            DeliveryTrackerProperties properties,
            @Value("${shipping.carrier.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${shipping.carrier.read-timeout-ms:5000}") int readTimeoutMs,
            MeterRegistry meterRegistry,
            Clock clock) {
        return new DeliveryTrackerTokenProvider(properties, connectTimeoutMs, readTimeoutMs, meterRegistry, clock);
    }

    @Bean
    CarrierTrackingPort deliveryTrackerCarrierTrackingAdapter(
            DeliveryTrackerProperties properties,
            DeliveryTrackerTokenProvider tokenProvider,
            @Value("${shipping.carrier.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${shipping.carrier.read-timeout-ms:5000}") int readTimeoutMs,
            MeterRegistry meterRegistry) {
        return new DeliveryTrackerCarrierTrackingAdapter(
                properties, tokenProvider, connectTimeoutMs, readTimeoutMs, meterRegistry);
    }
}
