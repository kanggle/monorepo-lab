package com.example.shipping.application.service;

import com.example.shipping.application.command.CarrierWebhookCommand;
import com.example.shipping.application.port.ShippingEventPublisher;
import com.example.shipping.application.port.WebhookDeliveryStore;
import com.example.shipping.application.service.ProcessCarrierWebhookService.WebhookOutcome;
import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;
import com.example.shipping.domain.repository.ShippingRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessCarrierWebhookServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock ShippingRepository shippingRepository;
    @Mock ShippingEventPublisher shippingEventPublisher;
    @Mock WebhookDeliveryStore webhookDeliveryStore;

    private ProcessCarrierWebhookService service;

    @BeforeEach
    void setUp() {
        service = new ProcessCarrierWebhookService(
                shippingRepository, shippingEventPublisher, webhookDeliveryStore, clock);
    }

    private Shipping shipped() {
        Shipping s = Shipping.create("order-1", "user-1", clock);
        s.transitionTo(ShippingStatus.SHIPPED, "TRK-1", "CJ", clock);
        return s;
    }

    private CarrierWebhookCommand cmd(String shippingId, String status) {
        return new CarrierWebhookCommand("delivery-1", shippingId, status);
    }

    @Test
    void firstDelivery_advancesForward_andPublishesNetChange() {
        Shipping shipping = shipped();
        String id = shipping.getShippingId();
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(true);
        when(shippingRepository.findById(id)).thenReturn(Optional.of(shipping));
        when(shippingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookOutcome outcome = service.ingest(cmd(id, "DELIVERED"));

        assertThat(outcome).isEqualTo(WebhookOutcome.ADVANCED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.DELIVERED);
        assertThat(shipping.getStatusHistory()).extracting(StatusHistoryEntry::status)
                .containsSubsequence(ShippingStatus.SHIPPED, ShippingStatus.IN_TRANSIT, ShippingStatus.DELIVERED);
        verify(shippingEventPublisher).publishShippingStatusChanged(
                eq(id), eq("order-1"), eq("user-1"),
                eq(ShippingStatus.SHIPPED), eq(ShippingStatus.DELIVERED), eq("TRK-1"), eq("CJ"));
    }

    @Test
    void duplicateDelivery_isNoOp_noShipmentAccess_noEvent() {
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(false);

        WebhookOutcome outcome = service.ingest(cmd("any-id", "DELIVERED"));

        assertThat(outcome).isEqualTo(WebhookOutcome.DUPLICATE);
        verify(shippingRepository, never()).findById(any());
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unmappedStatus_isNoOp() {
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(true);

        WebhookOutcome outcome = service.ingest(cmd("any-id", "WHO-KNOWS"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED);
        verify(shippingRepository, never()).findById(any());
        verify(shippingRepository, never()).save(any());
    }

    @Test
    void unknownShipment_isNoOp() {
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(true);
        when(shippingRepository.findById("missing")).thenReturn(Optional.empty());

        WebhookOutcome outcome = service.ingest(cmd("missing", "DELIVERED"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED);
        verify(shippingRepository, never()).save(any());
    }

    @Test
    void shipmentWithoutTrackingYet_isNoOp() {
        Shipping preparing = Shipping.create("order-2", "user-2", clock); // PREPARING, no tracking
        String id = preparing.getShippingId();
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(true);
        when(shippingRepository.findById(id)).thenReturn(Optional.of(preparing));

        WebhookOutcome outcome = service.ingest(cmd(id, "DELIVERED"));

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED);
        verify(shippingRepository, never()).save(any());
    }

    @Test
    void statusNotAhead_isNoOp_neverRegresses() {
        Shipping shipping = shipped(); // SHIPPED
        String id = shipping.getShippingId();
        when(webhookDeliveryStore.registerIfFirst("delivery-1")).thenReturn(true);
        when(shippingRepository.findById(id)).thenReturn(Optional.of(shipping));

        WebhookOutcome outcome = service.ingest(cmd(id, "SHIPPED")); // == current

        assertThat(outcome).isEqualTo(WebhookOutcome.IGNORED);
        assertThat(shipping.getStatus()).isEqualTo(ShippingStatus.SHIPPED);
        verify(shippingRepository, never()).save(any());
        verify(shippingEventPublisher, never())
                .publishShippingStatusChanged(any(), any(), any(), any(), any(), any(), any());
    }
}
