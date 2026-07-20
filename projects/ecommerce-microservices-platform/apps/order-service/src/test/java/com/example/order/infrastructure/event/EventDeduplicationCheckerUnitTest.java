package com.example.order.infrastructure.event;

import com.example.order.infrastructure.persistence.ProcessedEventJpaEntity;
import com.example.order.infrastructure.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventDeduplicationChecker 단위 테스트")
class EventDeduplicationCheckerUnitTest {

    @InjectMocks
    private EventDeduplicationChecker checker;

    @Mock
    private ProcessedEventJpaRepository processedEventJpaRepository;

    @Test
    @DisplayName("새로운 event_id인 경우 중복이 아니다")
    void isDuplicate_newEventId_returnsFalse() {
        when(processedEventJpaRepository.existsByEventId("evt-1")).thenReturn(false);

        boolean result = checker.isDuplicate("evt-1", "PaymentCompleted");

        assertThat(result).isFalse();
        verify(processedEventJpaRepository).save(any(ProcessedEventJpaEntity.class));
    }

    @Test
    @DisplayName("이미 처리된 event_id인 경우 중복이다")
    void isDuplicate_existingEventId_returnsTrue() {
        when(processedEventJpaRepository.existsByEventId("evt-1")).thenReturn(true);

        boolean result = checker.isDuplicate("evt-1", "PaymentCompleted");

        assertThat(result).isTrue();
        verify(processedEventJpaRepository, never()).save(any());
    }

    /*
     * TASK-BE-541 removed the test that used to sit here
     * ("동시 INSERT로 UNIQUE 제약 위반 시 중복으로 판단한다"). It stubbed
     * processedEventJpaRepository.save(...) to throw DataIntegrityViolationException and
     * asserted the checker returned true — but the real repository never throws from
     * save(): ProcessedEventJpaEntity has an assigned @Id, so the INSERT is queued until
     * the commit-time flush, which runs after isDuplicate() returns. The production catch
     * it exercised was unreachable, and this test is the reason nobody noticed.
     *
     * The catch is gone (see EventDeduplicationChecker). The concurrent-duplicate case is
     * now documented as being resolved by consumer retry, not by a catch, and that is a
     * transaction-boundary behaviour a Mockito unit test cannot express — asserting it
     * requires a real database and a real commit. It belongs in the integration lane, not
     * here. A replacement unit test would only re-create the same false confidence.
     */

    @Test
    @DisplayName("새 event_id는 중복이 아니고 처리 기록을 남긴다")
    void isDuplicate_newEventId_returnsFalseAndRecords() {
        when(processedEventJpaRepository.existsByEventId("evt-1")).thenReturn(false);

        boolean result = checker.isDuplicate("evt-1", "PaymentCompleted");

        assertThat(result).isFalse();
        verify(processedEventJpaRepository).save(any());
    }

    @Test
    @DisplayName("event_id가 null이면 중복 체크를 스킵한다")
    void isDuplicate_nullEventId_returnsFalse() {
        boolean result = checker.isDuplicate(null, "PaymentCompleted");

        assertThat(result).isFalse();
        verify(processedEventJpaRepository, never()).existsByEventId(any());
        verify(processedEventJpaRepository, never()).save(any());
    }

    @Test
    @DisplayName("event_id가 blank이면 중복 체크를 스킵한다")
    void isDuplicate_blankEventId_returnsFalse() {
        boolean result = checker.isDuplicate("  ", "PaymentCompleted");

        assertThat(result).isFalse();
        verify(processedEventJpaRepository, never()).existsByEventId(any());
        verify(processedEventJpaRepository, never()).save(any());
    }
}
