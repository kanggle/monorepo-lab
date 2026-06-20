package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.OrderServiceClient;
import com.example.batch.infrastructure.client.OrderServiceClient.ConfirmPaidStaleResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StalePaidOrderConfirmationJob} (TASK-BE-413 / AC-6).
 *
 * <p>Calls {@code execute()} directly — never through the scheduler — to bypass ShedLock
 * (the {@code lockAtLeastFor} trap: subsequent in-process invocations would silently no-op).
 *
 * <p>Covers:
 * <ul>
 *   <li>Success path: order-service returns {confirmed:N, skipped:M} → COMPLETED history +
 *       metrics incremented by N and M respectively</li>
 *   <li>Client throws (e.g. 401, timeout) → FAILED history recorded AND exception does NOT
 *       propagate (scheduler survives)</li>
 *   <li>Zero-work run: {scanned:0, confirmed:0, skipped:0} → COMPLETED, metrics unchanged</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("StalePaidOrderConfirmationJob 단위 테스트")
class StalePaidOrderConfirmationJobTest {

    @Mock
    private OrderServiceClient orderServiceClient;

    @Mock
    private BatchJobExecutionRepository executionRepository;

    private MeterRegistry meterRegistry;
    private StalePaidOrderConfirmationJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new StalePaidOrderConfirmationJob(orderServiceClient, executionRepository, meterRegistry);
        // Enable job (default is true; set explicitly for clarity)
        ReflectionTestUtils.setField(job, "enabled", true);

        // Default: save returns a reconstituted execution with a generated ID
        when(executionRepository.save(any(BatchJobExecution.class)))
                .thenAnswer(invocation -> {
                    BatchJobExecution arg = invocation.getArgument(0);
                    return BatchJobExecution.reconstitute(
                            arg.getId() != null ? arg.getId() : 1L,
                            arg.getJobName(),
                            arg.getStatus(),
                            arg.getStartedAt(),
                            arg.getFinishedAt(),
                            arg.getErrorMessage());
                });
    }

    @Test
    @DisplayName("성공 경로: confirmed=5, skipped=2 → COMPLETED 히스토리, 메트릭 +5/+2")
    void successPath_confirmedAndSkipped_completedHistoryAndMetricsIncremented() {
        // Arrange
        when(orderServiceClient.confirmPaidStale())
                .thenReturn(new ConfirmPaidStaleResponse(7, 5, 2, List.of("order-1", "order-2")));

        // Act
        job.execute();

        // Assert metrics
        Counter confirmedCounter = meterRegistry
                .find(StalePaidOrderConfirmationJob.CONFIRMED_COUNTER_NAME).counter();
        Counter skippedCounter = meterRegistry
                .find(StalePaidOrderConfirmationJob.SKIPPED_COUNTER_NAME).counter();
        assertThat(confirmedCounter).isNotNull();
        assertThat(skippedCounter).isNotNull();
        assertThat(confirmedCounter.count()).isEqualTo(5.0);
        assertThat(skippedCounter.count()).isEqualTo(2.0);

        // Assert COMPLETED history (second save — first is RUNNING start)
        ArgumentCaptor<BatchJobExecution> captor =
                ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, times(2)).save(captor.capture());
        List<BatchJobExecution> saved = captor.getAllValues();
        assertThat(saved.get(1).getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(saved.get(1).getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("클라이언트 예외 → FAILED 히스토리 기록, 예외 비전파 (스케줄러 생존)")
    void clientThrows_failedHistoryRecorded_exceptionNotPropagated() {
        // Arrange: order-service returns 401 (or any error causes RestClientException)
        when(orderServiceClient.confirmPaidStale())
                .thenThrow(new RuntimeException("order-service 401 Unauthorized"));

        // Act — must NOT throw (scheduler must survive)
        assertThatNoException().isThrownBy(() -> job.execute());

        // Assert: FAILED history was saved (two saves: start RUNNING + fail FAILED)
        ArgumentCaptor<BatchJobExecution> captor =
                ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, times(2)).save(captor.capture());

        List<BatchJobExecution> saved = captor.getAllValues();
        BatchJobExecution lastSave = saved.get(saved.size() - 1);
        assertThat(lastSave.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(lastSave.getErrorMessage()).contains("order-service 401 Unauthorized");

        // Metrics must NOT be incremented on failure
        Counter confirmedCounter = meterRegistry
                .find(StalePaidOrderConfirmationJob.CONFIRMED_COUNTER_NAME).counter();
        double confirmedCount = confirmedCounter != null ? confirmedCounter.count() : 0.0;
        assertThat(confirmedCount).isEqualTo(0.0);
    }

    @Test
    @DisplayName("스캔 0건 (no-op) → COMPLETED 히스토리, 메트릭 불변")
    void zeroWorkRun_completedHistory_metricsUnchanged() {
        // Arrange: nothing stale
        when(orderServiceClient.confirmPaidStale())
                .thenReturn(new ConfirmPaidStaleResponse(0, 0, 0, List.of()));

        // Act
        job.execute();

        // Assert: metrics stay at zero
        Counter confirmedCounter = meterRegistry
                .find(StalePaidOrderConfirmationJob.CONFIRMED_COUNTER_NAME).counter();
        Counter skippedCounter = meterRegistry
                .find(StalePaidOrderConfirmationJob.SKIPPED_COUNTER_NAME).counter();
        double confirmedCount = confirmedCounter != null ? confirmedCounter.count() : 0.0;
        double skippedCount = skippedCounter != null ? skippedCounter.count() : 0.0;
        assertThat(confirmedCount).isEqualTo(0.0);
        assertThat(skippedCount).isEqualTo(0.0);

        // Assert: COMPLETED history
        ArgumentCaptor<BatchJobExecution> captor =
                ArgumentCaptor.forClass(BatchJobExecution.class);
        verify(executionRepository, times(2)).save(captor.capture());
        List<BatchJobExecution> saved = captor.getAllValues();
        assertThat(saved.get(1).getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }
}
