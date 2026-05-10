package com.wms.outbound.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wms.outbound.application.command.RetryTmsNotificationCommand;
import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.result.RetryTmsNotificationResult;
import com.wms.outbound.application.service.RetryTmsPersistenceHelper.ShipmentSnapshot;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import com.wms.outbound.domain.exception.TmsRetryNotAllowedException;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.domain.model.TmsStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/**
 * Unit tests for {@link RetryTmsNotificationService}.
 */
@ExtendWith(MockitoExtension.class)
class RetryTmsNotificationServiceTest {

    private static final UUID SHIPMENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SAGA_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");
    private static final String ACTOR = "user-99";

    @Mock
    private ShipmentNotificationPort tmsPort;
    @Mock
    private RetryTmsPersistenceHelper helper;

    private MeterRegistry meterRegistry;
    private RetryTmsNotificationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        service = new RetryTmsNotificationService(tmsPort, helper, meterRegistry, clock);
    }

    @Test
    void requiresAdminRole() {
        RetryTmsNotificationCommand cmd = new RetryTmsNotificationCommand(
                SHIPMENT_ID, ACTOR, Set.of("ROLE_OUTBOUND_WRITE"));

        assertThatThrownBy(() -> service.retry(cmd))
                .isInstanceOf(AccessDeniedException.class);

        verify(tmsPort, never()).notify(any());
        verify(helper, never()).loadAndValidate(any());
    }

    @Test
    void rejectsWhenShipmentNotInNotifyFailed() {
        RetryTmsNotificationCommand cmd = adminCommand();
        when(helper.loadAndValidate(SHIPMENT_ID))
                .thenThrow(new TmsRetryNotAllowedException(SHIPMENT_ID, "NOTIFIED"));

        assertThatThrownBy(() -> service.retry(cmd))
                .isInstanceOf(TmsRetryNotAllowedException.class);
        verify(tmsPort, never()).notify(any());
    }

    @Test
    void onSuccess_delegatesToHelperAndReturnsCompleted() {
        RetryTmsNotificationCommand cmd = adminCommand();
        ShipmentSnapshot snapshot = new ShipmentSnapshot(
                TmsStatus.NOTIFY_FAILED, null, null, SAGA_ID, SagaStatus.SHIPPED_NOT_NOTIFIED);
        TmsAcknowledgement ack = new TmsAcknowledgement(true, UUID.randomUUID().toString(), "TRK-1", "CJ");
        RetryTmsNotificationResult expected = new RetryTmsNotificationResult(
                SHIPMENT_ID, "NOTIFIED", T0, "TRK-1", "COMPLETED", T0, ACTOR);

        when(helper.loadAndValidate(SHIPMENT_ID)).thenReturn(snapshot);
        when(tmsPort.notify(SHIPMENT_ID)).thenReturn(ack);
        when(helper.markRetrySucceeded(eq(cmd), eq(ack), eq(T0))).thenReturn(expected);

        RetryTmsNotificationResult actual = service.retry(cmd);

        assertThat(actual).isEqualTo(expected);
        verify(helper, times(1)).markRetrySucceeded(eq(cmd), eq(ack), eq(T0));
        // No alert metric on success.
        assertThat(meterRegistry.counter("outbound.alert.tms.notify.failure", "vendor", "tms").count())
                .isEqualTo(0.0);
    }

    @Test
    void onAdapterExternalUnavailable_returnsFailedSnapshot_andIncrementsAlert() {
        RetryTmsNotificationCommand cmd = adminCommand();
        ShipmentSnapshot snapshot = new ShipmentSnapshot(
                TmsStatus.NOTIFY_FAILED, null, null, SAGA_ID, SagaStatus.SHIPPED_NOT_NOTIFIED);

        when(helper.loadAndValidate(SHIPMENT_ID)).thenReturn(snapshot);
        when(tmsPort.notify(SHIPMENT_ID))
                .thenThrow(new ExternalServiceUnavailableException("tms", "circuit open"));

        RetryTmsNotificationResult result = service.retry(cmd);

        assertThat(result.tmsStatus()).isEqualTo("NOTIFY_FAILED");
        assertThat(result.sagaState()).isEqualTo("SHIPPED_NOT_NOTIFIED");
        verify(helper, never()).markRetrySucceeded(any(), any(), any());
        assertThat(meterRegistry.counter("outbound.alert.tms.notify.failure", "vendor", "tms").count())
                .isEqualTo(1.0);
    }

    @Test
    void onUnsuccessfulAck_returnsFailedSnapshot_andIncrementsAlert() {
        RetryTmsNotificationCommand cmd = adminCommand();
        ShipmentSnapshot snapshot = new ShipmentSnapshot(
                TmsStatus.NOTIFY_FAILED, null, null, SAGA_ID, SagaStatus.SHIPPED_NOT_NOTIFIED);
        TmsAcknowledgement ack = new TmsAcknowledgement(false, "vendor-req-x", null, null);

        when(helper.loadAndValidate(SHIPMENT_ID)).thenReturn(snapshot);
        when(tmsPort.notify(SHIPMENT_ID)).thenReturn(ack);

        RetryTmsNotificationResult result = service.retry(cmd);

        assertThat(result.tmsStatus()).isEqualTo("NOTIFY_FAILED");
        assertThat(result.sagaState()).isEqualTo("SHIPPED_NOT_NOTIFIED");
        verify(helper, never()).markRetrySucceeded(any(), any(), any());
        assertThat(meterRegistry.counter("outbound.alert.tms.notify.failure", "vendor", "tms").count())
                .isEqualTo(1.0);
    }

    private static RetryTmsNotificationCommand adminCommand() {
        return new RetryTmsNotificationCommand(
                SHIPMENT_ID, ACTOR, Set.of("ROLE_OUTBOUND_ADMIN"));
    }
}
