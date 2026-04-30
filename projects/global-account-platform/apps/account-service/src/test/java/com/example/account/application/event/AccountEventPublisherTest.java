package com.example.account.application.event;

import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link AccountEventPublisher} focused on the
 * TASK-BE-041b-fix contract change: the flat {@code account.locked} payload
 * must carry an {@code eventId} (UUID) so downstream consumers can
 * idempotently deduplicate Kafka at-least-once redeliveries.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AccountEventPublisher publisher;

    private Account account(String id) {
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "user@example.com", null,
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
    }

    @Test
    @DisplayName("publishAccountLocked payload includes a UUID eventId (idempotency key)")
    void publishAccountLockedIncludesEventId() throws Exception {
        publisher.publishAccountLocked(
                account("acc-1"), "ADMIN_LOCK", "operator", "op-7",
                Instant.parse("2026-04-14T10:00:00Z"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Account"), eq("acc-1"), eq("account.locked"), payloadCaptor.capture());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.has("eventId"))
                .as("account.locked flat payload must carry eventId per TASK-BE-041b-fix")
                .isTrue();
        String eventId = payload.get("eventId").asText();
        assertThat(eventId).isNotBlank();
        // Must be a valid UUID — parseable and in canonical form.
        UUID parsed = UUID.fromString(eventId);
        assertThat(parsed.toString()).isEqualTo(eventId);
        // Other required fields preserved.
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("reasonCode").asText()).isEqualTo("ADMIN_LOCK");
        assertThat(payload.get("actorType").asText()).isEqualTo("operator");
        assertThat(payload.get("actorId").asText()).isEqualTo("op-7");
        assertThat(payload.get("lockedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("Each publishAccountLocked call generates a fresh eventId")
    void eventIdIsUniquePerCall() throws Exception {
        publisher.publishAccountLocked(account("acc-1"), "ADMIN_LOCK", "operator", "op-1",
                Instant.parse("2026-04-14T10:00:00Z"));
        publisher.publishAccountLocked(account("acc-2"), "AUTO_DETECT", "system", null,
                Instant.parse("2026-04-14T10:00:01Z"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter, org.mockito.Mockito.times(2))
                .save(any(), any(), eq("account.locked"), captor.capture());

        String e1 = objectMapper.readTree(captor.getAllValues().get(0)).get("eventId").asText();
        String e2 = objectMapper.readTree(captor.getAllValues().get(1)).get("eventId").asText();
        assertThat(e1).isNotEqualTo(e2);
    }
}
