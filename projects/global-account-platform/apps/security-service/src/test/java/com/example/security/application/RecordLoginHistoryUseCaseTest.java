package com.example.security.application;

import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.example.security.domain.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordLoginHistoryUseCaseTest {

    @Mock
    private LoginHistoryRepository loginHistoryRepository;

    @Mock
    private ProcessedEventJpaRepository processedEventRepository;

    @InjectMocks
    private RecordLoginHistoryUseCase useCase;

    private LoginHistoryEntry createEntry(String eventId) {
        return new LoginHistoryEntry(
                eventId, "acc-001", LoginOutcome.SUCCESS,
                "192.168.1.***", "Chrome 120", "abcdef123456",
                "KR", Instant.now()
        );
    }

    @Test
    @DisplayName("Saves entry and marks processed when event is new")
    void savesNewEvent() {
        when(processedEventRepository.existsByEventId("evt-001")).thenReturn(false);

        boolean result = useCase.execute(createEntry("evt-001"), "auth.login.succeeded");

        assertThat(result).isTrue();
        verify(loginHistoryRepository).save(any(LoginHistoryEntry.class));
        verify(processedEventRepository).save(any());
    }

    @Test
    @DisplayName("Returns false when event already exists in processed_events")
    void skipsDuplicateEvent() {
        when(processedEventRepository.existsByEventId("evt-002")).thenReturn(true);

        boolean result = useCase.execute(createEntry("evt-002"), "auth.login.succeeded");

        assertThat(result).isFalse();
        verify(loginHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Handles concurrent duplicate via DataIntegrityViolationException")
    void handlesConcurrentDuplicate() {
        when(processedEventRepository.existsByEventId("evt-003")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("Duplicate entry"))
                .when(loginHistoryRepository).save(any());

        boolean result = useCase.execute(createEntry("evt-003"), "auth.login.succeeded");

        assertThat(result).isFalse();
    }
}
