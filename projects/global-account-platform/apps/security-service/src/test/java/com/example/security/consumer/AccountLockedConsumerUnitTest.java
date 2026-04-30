package com.example.security.consumer;

import com.example.security.infrastructure.persistence.AccountLockHistoryJpaEntity;
import com.example.security.infrastructure.persistence.AccountLockHistoryJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountLockedConsumerUnitTest {

    @Mock
    private AccountLockHistoryJpaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AccountLockedConsumer consumer;

    AccountLockedConsumerUnitTest() {
        // Manual wiring because @InjectMocks cannot inject the concrete ObjectMapper bean.
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("account.locked", 0, 0L, "key", value);
    }

    private AccountLockedConsumer newConsumer() {
        return new AccountLockedConsumer(objectMapper, repository);
    }

    @Test
    @DisplayName("Envelope-wrapped payload is parsed and saved with reasonCode/actorType mapping")
    void envelopeWrappedPayloadPersisted() {
        String json = """
            {
              "eventId": "11111111-1111-1111-1111-111111111111",
              "eventType": "account.locked",
              "source": "account-service",
              "occurredAt": "2026-04-14T10:00:00Z",
              "schemaVersion": 1,
              "partitionKey": "acc-1",
              "payload": {
                "accountId": "acc-1",
                "reasonCode": "ADMIN_LOCK",
                "actorType": "operator",
                "actorId": "op-42",
                "lockedAt": "2026-04-14T10:00:00Z"
              }
            }
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(AccountLockHistoryJpaEntity.class);
        verify(repository).save(captor.capture());
        AccountLockHistoryJpaEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getReason()).isEqualTo("ADMIN_LOCK");
        assertThat(saved.getLockedBy()).isEqualTo("op-42");
        assertThat(saved.getSource()).isEqualTo("admin");
        assertThat(saved.getOccurredAt()).isEqualTo(Instant.parse("2026-04-14T10:00:00Z"));
    }

    @Test
    @DisplayName("Flat payload (account-service current form) is also accepted")
    void flatPayloadAccepted() {
        String json = """
            {
              "eventId": "55555555-5555-5555-5555-555555555555",
              "accountId": "acc-2",
              "reasonCode": "AUTO_DETECT",
              "actorType": "system",
              "lockedAt": "2026-04-14T11:00:00Z"
            }
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(AccountLockHistoryJpaEntity.class);
        verify(repository).save(captor.capture());
        AccountLockHistoryJpaEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("55555555-5555-5555-5555-555555555555");
        assertThat(saved.getAccountId()).isEqualTo("acc-2");
        assertThat(saved.getSource()).isEqualTo("system");
        // Missing actorId falls back to the zero UUID convention (edge case: system-driven lock)
        assertThat(saved.getLockedBy()).isEqualTo("00000000-0000-0000-0000-000000000000");
        assertThat(saved.getReason()).isEqualTo("AUTO_DETECT");
    }

    @Test
    @DisplayName("TASK-BE-041b-fix: missing eventId propagates so DefaultErrorHandler routes to DLQ")
    void missingEventIdThrows() {
        String json = """
            {
              "accountId": "acc-no-event-id",
              "reasonCode": "ADMIN_LOCK",
              "actorType": "operator",
              "actorId": "op-1",
              "lockedAt": "2026-04-14T11:00:00Z"
            }
            """;
        assertThatThrownBy(() -> newConsumer().onMessage(record(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Duplicate event_id triggers DataIntegrityViolation which is swallowed (idempotent)")
    void duplicateEventIdSwallowed() {
        doThrow(new DataIntegrityViolationException("uk_account_lock_history_event_id"))
                .when(repository).save(any());

        String json = """
            {"eventId":"22222222-2222-2222-2222-222222222222",
             "payload":{"accountId":"acc-3","reasonCode":"ADMIN_LOCK",
                        "actorType":"operator","actorId":"op-1","lockedAt":"2026-04-14T12:00:00Z"}}
            """;

        // Must not throw — DLQ routing should only happen for genuine failures.
        newConsumer().onMessage(record(json));
        verify(repository).save(any());
    }

    @Test
    @DisplayName("Malformed JSON propagates RuntimeException so DefaultErrorHandler routes to DLQ")
    void malformedJsonThrows() {
        assertThatThrownBy(() -> newConsumer().onMessage(record("not json {{{")))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Missing accountId propagates RuntimeException so DefaultErrorHandler routes to DLQ")
    void missingAccountIdThrows() {
        String json = """
            {"eventId":"33333333-3333-3333-3333-333333333333",
             "payload":{"reasonCode":"ADMIN_LOCK","actorType":"operator","actorId":"op-1"}}
            """;
        assertThatThrownBy(() -> newConsumer().onMessage(record(json)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Explicit source field overrides actorType-derived default")
    void explicitSourceWins() {
        String json = """
            {"eventId":"44444444-4444-4444-4444-444444444444",
             "payload":{"accountId":"acc-4","reason":"manual","lockedBy":"op-9",
                        "source":"admin","occurredAt":"2026-04-14T13:00:00Z"}}
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistoryJpaEntity> captor =
                ArgumentCaptor.forClass(AccountLockHistoryJpaEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("admin");
        assertThat(captor.getValue().getLockedBy()).isEqualTo("op-9");
    }
}
