package com.wms.notification.adapter.outbound.scheduling;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.wms.notification.application.port.in.RetryFailedDeliveryUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit coverage for the {@code @Scheduled} wrapper (TASK-BE-528 AC-3). The
 * poll tick delegates to {@link RetryFailedDeliveryUseCase#dispatchDueRetries()}
 * and must swallow a thrown {@link RuntimeException} so one bad tick does not
 * kill the scheduler thread.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryRetrySchedulerTest {

    @Mock
    private RetryFailedDeliveryUseCase retry;

    @InjectMocks
    private DeliveryRetryScheduler scheduler;

    @Test
    void pollDelegatesToDispatchDueRetries() {
        when(retry.dispatchDueRetries()).thenReturn(3);

        scheduler.poll();

        verify(retry).dispatchDueRetries();
        verifyNoMoreInteractions(retry);
    }

    @Test
    void pollSwallowsRuntimeExceptionFromDispatch() {
        when(retry.dispatchDueRetries()).thenThrow(new RuntimeException("db down"));

        // Must NOT propagate — a failed tick is logged and the schedule survives.
        scheduler.poll();

        verify(retry).dispatchDueRetries();
    }
}
