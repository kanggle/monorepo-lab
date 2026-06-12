package com.example.shipping.application.service;

import com.example.shipping.application.port.CarrierTrackingPort;
import com.example.shipping.application.port.CarrierTrackingPort.CarrierTrackingSnapshot;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.service.AutoCollectTrackingService.SweepResult;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.repository.ShippingRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the unattended auto-collect sweep (TASK-BE-360). Every test calls
 * {@link AutoCollectTrackingService#sweep(int)} <b>directly</b> (bypassing the scheduler bean's
 * ShedLock — the documented ShedLock IT trap), asserting the advance / forward-only / no-op /
 * per-item-isolation business logic on the shared {@link CarrierAdvanceProcessor}.
 */
@ExtendWith(MockitoExtension.class)
class AutoCollectTrackingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock ShippingRepository shippingRepository;
    @Mock ShippingEventPublisher shippingEventPublisher;
    @Mock CarrierTrackingPort carrierTrackingPort;

    private SimpleMeterRegistry meterRegistry;
    private AutoCollectTrackingService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CarrierAdvanceProcessor processor = new CarrierAdvanceProcessor(
                shippingRepository, shippingEventPublisher, carrierTrackingPort,
                new CarrierStatusObserver(meterRegistry), clock);
        service = new AutoCollectTrackingService(shippingRepository, processor, meterRegistry, 100);
    }

    /** A SHIPPED shipment with tracking + carrier (the realistic in-flight sweep subject). */
    private Shipping shipped(String orderId, String trackingNumber) {
        Shipping s = Shipping.create(orderId, "user-" + orderId, clock);
        s.transitionTo(ShippingStatus.SHIPPED, trackingNumber, "CJ", clock);
        return s;
    }

    @Test
    void emptyInFlight_isCleanNoOp() {
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of());

        SweepResult result = service.sweep();

        assertThat(result).isEqualTo(new SweepResult(0, 0, 0, 0));
        verify(carrierTrackingPort, never()).fetchLatest(any(), any());
        verify(shippingRepository, never()).save(any());
    }

    @Test
    void aheadCarrierStatus_advancesForward_andPublishesEvent() {
        Shipping shipping = shipped("order-1", "TRK-1");
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1"))
                .thenReturn(java.util.Optional.of(new CarrierTrackingSnapshot("DELIVERED")));
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SweepResult result = service.sweep();

        assertThat(result).isEqualTo(new SweepResult(1, 1, 0, 0));
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        verify(shippingEventPublisher).publishShippingStatusChanged(
                eq(shipping.getShippingId()), eq("order-1"), eq("user-order-1"),
                eq(ShippingStatus.SHIPPED), eq(ShippingStatus.DELIVERED), eq("TRK-1"), eq("CJ"));
        assertThat(meterRegistry.get("carrier_auto_collect_processed")
                .tags("outcome", "advanced").counter().count()).isEqualTo(1.0);
    }

    @Test
    void notAheadCarrierStatus_isNoOp_forwardOnly() {
        Shipping shipping = shipped("order-2", "TRK-2"); // current SHIPPED
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-2"))
                .thenReturn(java.util.Optional.of(new CarrierTrackingSnapshot("SHIPPED"))); // == current

        SweepResult result = service.sweep();

        assertThat(result).isEqualTo(new SweepResult(1, 0, 1, 0));
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void carrierOutageOnOneItem_isIsolated_othersProceed() {
        Shipping outage = shipped("order-3", "TRK-3");
        Shipping ok = shipped("order-4", "TRK-4");
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of(outage, ok));
        // item 3: carrier port throws (outage) → caught, counted failed, batch continues
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-3"))
                .thenThrow(new RuntimeException("carrier outage"));
        // item 4: advances normally
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-4"))
                .thenReturn(java.util.Optional.of(new CarrierTrackingSnapshot("IN_TRANSIT")));
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SweepResult result = service.sweep();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.advanced()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(ok.getStatus()).isEqualTo(ShippingStatus.IN_TRANSIT); // unaffected by item-3 failure
        assertThat(outage.getStatus()).isEqualTo(ShippingStatus.SHIPPED); // unchanged
        assertThat(meterRegistry.get("carrier_auto_collect_processed")
                .tags("outcome", "failed").counter().count()).isEqualTo(1.0);
    }

    @Test
    void unmappedCarrierStatus_isNoOp_andDoesNotAbortBatch() {
        Shipping unmapped = shipped("order-5", "TRK-5");
        Shipping ok = shipped("order-6", "TRK-6");
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of(unmapped, ok));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-5"))
                .thenReturn(java.util.Optional.of(new CarrierTrackingSnapshot("통관보류"))); // unmapped
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-6"))
                .thenReturn(java.util.Optional.of(new CarrierTrackingSnapshot("DELIVERED")));
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SweepResult result = service.sweep();

        assertThat(result.processed()).isEqualTo(2);
        assertThat(result.advanced()).isEqualTo(1);
        assertThat(result.noOp()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(unmapped.getStatus()).isEqualTo(ShippingStatus.SHIPPED); // unmapped = no-op
        assertThat(ok.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        // unmapped non-blank status is made observable
        assertThat(meterRegistry.get("carrier_status_unmapped")
                .tags("source", "auto-collect", "raw_status", "통관보류").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void carrierUnavailable_isNoOp_notCountedUnmapped() {
        Shipping shipping = shipped("order-7", "TRK-7");
        when(shippingRepository.findInFlightWithTracking(100)).thenReturn(List.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-7")).thenReturn(java.util.Optional.empty());

        SweepResult result = service.sweep();

        assertThat(result).isEqualTo(new SweepResult(1, 0, 1, 0));
        verify(shippingRepository, never()).save(any());
        // blank/absent carrier signal carries no info → not counted as unmapped (net-zero)
        assertThat(meterRegistry.find("carrier_status_unmapped").counter()).isNull();
    }

    @Test
    void batchSizeOverride_isHonoured() {
        when(shippingRepository.findInFlightWithTracking(25)).thenReturn(List.of());

        service.sweep(25);

        verify(shippingRepository).findInFlightWithTracking(25);
    }
}
