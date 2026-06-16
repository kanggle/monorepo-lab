package com.example.security.application;

import com.example.security.domain.history.AccountLockHistory;
import com.example.security.domain.repository.AccountLockHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecordAccountLockHistoryUseCaseTest {

    @Mock
    private AccountLockHistoryRepository repository;

    @InjectMocks
    private RecordAccountLockHistoryUseCase useCase;

    private AccountLockHistory sample() {
        return new AccountLockHistory(
                "fan-platform", "11111111-1111-1111-1111-111111111111", "acc-1",
                "ADMIN_LOCK", "op-42", "admin", Instant.parse("2026-04-14T10:00:00Z"));
    }

    @Test
    @DisplayName("New entry is saved and reported as recorded")
    void recordsNewEntry() {
        AccountLockHistory entry = sample();

        boolean recorded = useCase.execute(entry);

        assertThat(recorded).isTrue();
        verify(repository).save(entry);
    }

    @Test
    @DisplayName("Duplicate event_id (DataIntegrityViolation) is swallowed and reported as not-recorded")
    void duplicateEventIdSwallowed() {
        doThrow(new DataIntegrityViolationException("uk_account_lock_history_event_id"))
                .when(repository).save(any());

        // Must not throw — a duplicate replay is idempotent success, not a DLQ-worthy failure.
        assertThatCode(() -> {
            boolean recorded = useCase.execute(sample());
            assertThat(recorded).isFalse();
        }).doesNotThrowAnyException();
    }
}
