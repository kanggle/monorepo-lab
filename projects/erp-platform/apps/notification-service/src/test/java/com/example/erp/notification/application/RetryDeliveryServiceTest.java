package com.example.erp.notification.application;

import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RetryDeliveryServiceTest {

    @Mock NotificationDeliveryRepository deliveryRepository;
    @Mock DeliveryAttemptProcessor attemptProcessor;

    private RetryDeliveryService service;

    private final Instant now = Instant.parse("2026-06-12T00:00:00Z");

    @BeforeEach
    void setUp() {
        ExternalNotificationProperties props = new ExternalNotificationProperties(); // batchSize 50
        service = new RetryDeliveryService(deliveryRepository, attemptProcessor, props);
    }

    @Test
    void processesEachDueDelivery() {
        when(deliveryRepository.findDueDeliveryIds(now, 50)).thenReturn(List.of("d1", "d2", "d3"));

        int count = service.runDue(now);

        assertThat(count).isEqualTo(3);
        verify(attemptProcessor).attempt("d1", now);
        verify(attemptProcessor).attempt("d2", now);
        verify(attemptProcessor).attempt("d3", now);
    }

    @Test
    void noDueDeliveries_isNoOp() {
        when(deliveryRepository.findDueDeliveryIds(now, 50)).thenReturn(List.of());

        int count = service.runDue(now);

        assertThat(count).isZero();
        verify(attemptProcessor, never()).attempt(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void oneFailingDelivery_doesNotStopTheSweep() {
        when(deliveryRepository.findDueDeliveryIds(now, 50)).thenReturn(List.of("d1", "d2"));
        doThrow(new RuntimeException("boom")).when(attemptProcessor).attempt("d1", now);

        int count = service.runDue(now);

        assertThat(count).isEqualTo(2);
        verify(attemptProcessor).attempt("d2", now); // still processed despite d1 failing
    }
}
