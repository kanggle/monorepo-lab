package com.example.order.infrastructure.event;

import com.example.order.infrastructure.persistence.ProcessedEventJpaEntity;
import com.example.order.infrastructure.persistence.ProcessedEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    @DisplayName("동시 INSERT로 UNIQUE 제약 위반 시 중복으로 판단한다")
    void isDuplicate_concurrentInsert_returnsTrue() {
        when(processedEventJpaRepository.existsByEventId("evt-1")).thenReturn(false);
        when(processedEventJpaRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        boolean result = checker.isDuplicate("evt-1", "PaymentCompleted");

        assertThat(result).isTrue();
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
