package com.example.security.consumer;

import com.example.security.application.RecordAccountLockHistoryUseCase;
import com.example.security.domain.history.AccountLockHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AccountLockedConsumerUnitTest {

    @Mock
    private RecordAccountLockHistoryUseCase useCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("account.locked", 0, 0L, "key", value);
    }

    private AccountLockedConsumer newConsumer() {
        // Manual wiring because the concrete ObjectMapper bean is not a mock.
        return new AccountLockedConsumer(objectMapper, useCase);
    }

    @Test
    @DisplayName("Envelope-wrapped payload is parsed and delegated with reasonCode/actorType mapping")
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
                "tenantId": "fan-platform",
                "reasonCode": "ADMIN_LOCK",
                "actorType": "operator",
                "actorId": "op-42",
                "lockedAt": "2026-04-14T10:00:00Z"
              }
            }
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistory> captor = ArgumentCaptor.forClass(AccountLockHistory.class);
        verify(useCase).execute(captor.capture());
        AccountLockHistory saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(saved.getTenantId()).isEqualTo("fan-platform");
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
              "tenantId": "fan-platform",
              "reasonCode": "AUTO_DETECT",
              "actorType": "system",
              "lockedAt": "2026-04-14T11:00:00Z"
            }
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistory> captor = ArgumentCaptor.forClass(AccountLockHistory.class);
        verify(useCase).execute(captor.capture());
        AccountLockHistory saved = captor.getValue();
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
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("Malformed JSON propagates RuntimeException so DefaultErrorHandler routes to DLQ")
    void malformedJsonThrows() {
        assertThatThrownBy(() -> newConsumer().onMessage(record("not json {{{")))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(useCase);
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
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("Explicit source field overrides actorType-derived default")
    void explicitSourceWins() {
        String json = """
            {"eventId":"44444444-4444-4444-4444-444444444444",
             "payload":{"accountId":"acc-4","tenantId":"fan-platform","reason":"manual","lockedBy":"op-9",
                        "source":"admin","occurredAt":"2026-04-14T13:00:00Z"}}
            """;

        newConsumer().onMessage(record(json));

        ArgumentCaptor<AccountLockHistory> captor = ArgumentCaptor.forClass(AccountLockHistory.class);
        verify(useCase).execute(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("admin");
        assertThat(captor.getValue().getLockedBy()).isEqualTo("op-9");
    }

    @Test
    @DisplayName("TASK-BE-260: account.locked payload missing tenantId throws MissingTenantIdException (Phase 2b strict mode)")
    void missingTenantIdThrows() {
        String json = """
            {"eventId":"66666666-6666-6666-6666-666666666666",
             "payload":{"accountId":"acc-no-tenant","reasonCode":"ADMIN_LOCK",
                        "actorType":"operator","actorId":"op-1","lockedAt":"2026-04-14T10:00:00Z"}}
            """;
        assertThatThrownBy(() -> newConsumer().onMessage(record(json)))
                .isInstanceOf(MissingTenantIdException.class);
        verifyNoInteractions(useCase);
    }
}
