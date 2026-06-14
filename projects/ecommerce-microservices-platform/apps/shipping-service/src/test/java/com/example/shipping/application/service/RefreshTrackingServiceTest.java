package com.example.shipping.application.service;

import com.example.shipping.application.port.CarrierTrackingPort;
import com.example.shipping.application.port.CarrierTrackingPort.CarrierTrackingSnapshot;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.result.UpdateShippingStatusResult;
import com.example.web.exception.AccessDeniedException;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTrackingServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock ShippingRepository shippingRepository;
    @Mock ShippingEventPublisher shippingEventPublisher;
    @Mock CarrierTrackingPort carrierTrackingPort;

    private SimpleMeterRegistry meterRegistry;
    private RefreshTrackingService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        CarrierAdvanceProcessor processor = new CarrierAdvanceProcessor(
                shippingRepository, shippingEventPublisher, carrierTrackingPort,
                new CarrierStatusObserver(meterRegistry), clock);
        service = new RefreshTrackingService(shippingRepository, processor);
    }

    /** A SHIPPED shipment with tracking + carrier set (the realistic refresh subject). */
    private Shipping shipped() {
        Shipping s = Shipping.create("tenant-a", "order-1", "user-1", clock);
        s.transitionTo(ShippingStatus.SHIPPED, "TRK-1", "CJ", clock);
        return s;
    }

    @Test
    void advancesForwardToCarrierStatus_andPublishesNetChange() {
        Shipping shipping = shipped();
        String id = shipping.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1"))
                .thenReturn(Optional.of(new CarrierTrackingSnapshot("DELIVERED")));
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        // advanced through the linear chain → history records the intermediate IN_TRANSIT
        assertThat(shipping.getStatusHistory()).extracting(StatusHistoryEntry::status)
                .containsSubsequence(ShippingStatus.SHIPPED, ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED);
        verify(shippingRepository).save(shipping);
        verify(shippingEventPublisher).publishShippingStatusChanged(
                eq("tenant-a"), eq(id), eq("order-1"), eq("user-1"),
                eq(ShippingStatus.SHIPPED), eq(ShippingStatus.DELIVERED), eq("TRK-1"), eq("CJ"));
    }

    @Test
    void carrierUnavailable_isNoOp() {
        Shipping shipping = shipped();
        String id = shipping.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1")).thenReturn(Optional.empty());

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any(), any());
        // a blank/absent carrier signal carries no info → not counted as unmapped (net-zero)
        assertThat(meterRegistry.find("carrier_status_unmapped").counter()).isNull();
    }

    @Test
    void unmappedAggregatorStatus_isNoOp_andCountsUnmapped() {
        Shipping shipping = shipped();
        String id = shipping.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1"))
                .thenReturn(Optional.of(new CarrierTrackingSnapshot("통관보류"))); // unmapped aggregator code

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.SHIPPED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(meterRegistry.get("carrier_status_unmapped")
                .tags("source", "refresh", "raw_status", "통관보류").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void mappedAggregatorToken_advancesForward() {
        Shipping shipping = shipped(); // SHIPPED
        String id = shipping.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1"))
                .thenReturn(Optional.of(new CarrierTrackingSnapshot("배송중"))); // aggregator Korean unified code
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.IN_TRANSIT);
        assertThat(meterRegistry.find("carrier_status_unmapped").counter()).isNull();
    }

    @Test
    void carrierStatusNotAhead_isNoOp() {
        Shipping shipping = shipped(); // current SHIPPED
        String id = shipping.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(shipping));
        when(carrierTrackingPort.fetchLatest("CJ", "TRK-1"))
                .thenReturn(Optional.of(new CarrierTrackingSnapshot("SHIPPED"))); // == current

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void noTrackingYet_isNoOp_andCarrierNotCalled() {
        Shipping preparing = Shipping.create("tenant-a", "order-2", "user-2", clock); // PREPARING, no tracking
        String id = preparing.getShippingId();
        when(shippingRepository.findByIdForTenant(id)).thenReturn(Optional.of(preparing));

        UpdateShippingStatusResult result = service.refreshFromCarrier(id, "ADMIN");

        assertThat(result.status()).isEqualTo(ShippingStatus.PREPARING);
        verify(carrierTrackingPort, never()).fetchLatest(any(), any());
        verify(shippingRepository, never()).save(any());
    }

    @Test
    void nonAdmin_isRejected() {
        assertThatThrownBy(() -> service.refreshFromCarrier("any-id", "USER"))
                .isInstanceOf(AccessDeniedException.class);
        verify(shippingRepository, never()).findByIdForTenant(any());
    }
}
