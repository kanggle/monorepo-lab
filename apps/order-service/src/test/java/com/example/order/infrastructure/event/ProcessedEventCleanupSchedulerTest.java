package com.example.order.infrastructure.event;

import com.example.messaging.outbox.ProcessedEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessedEventCleanupScheduler 단위 테스트")
class ProcessedEventCleanupSchedulerTest {

    @InjectMocks
    private ProcessedEventCleanupScheduler scheduler;

    @Mock
    private ProcessedEventJpaRepository processedEventJpaRepository;

    @Captor
    private ArgumentCaptor<LocalDateTime> cutoffCaptor;

    @Test
    @DisplayName("cleanup 실행 시 deleteByProcessedAtBefore를 현재 시각 기준 30일 전으로 호출한다")
    void cleanup_always_callsDeleteWithCutoffApproximately30DaysAgo() {
        LocalDateTime before = LocalDateTime.now().minusDays(30);
        given(processedEventJpaRepository.deleteByProcessedAtBefore(cutoffCaptor.capture())).willReturn(0);

        scheduler.cleanup();

        LocalDateTime captured = cutoffCaptor.getValue();

        assertThat(captured).isCloseTo(before, within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("삭제된 레코드가 0건일 때 cleanup이 정상적으로 완료된다")
    void cleanup_whenZeroRecordsDeleted_completesNormally() {
        given(processedEventJpaRepository.deleteByProcessedAtBefore(any(LocalDateTime.class)))
                .willReturn(0);

        scheduler.cleanup();

        then(processedEventJpaRepository).should().deleteByProcessedAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("삭제된 레코드가 여러 건일 때 cleanup이 정상적으로 완료된다")
    void cleanup_whenMultipleRecordsDeleted_completesNormally() {
        given(processedEventJpaRepository.deleteByProcessedAtBefore(any(LocalDateTime.class)))
                .willReturn(150);

        scheduler.cleanup();

        then(processedEventJpaRepository).should().deleteByProcessedAtBefore(any(LocalDateTime.class));
    }
}
