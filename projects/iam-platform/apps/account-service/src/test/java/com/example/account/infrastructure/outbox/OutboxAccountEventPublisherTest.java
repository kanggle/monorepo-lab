package com.example.account.infrastructure.outbox;

import com.example.account.application.event.AccountEventFactory;
import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.persistence.AccountOutboxJpaEntity;
import com.example.account.infrastructure.persistence.AccountOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxAccountEventPublisher} (TASK-BE-451 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code AccountEventPublisherTest} which mocked the
 * lib {@code OutboxWriter}; now we mock the per-service
 * {@link AccountOutboxJpaRepository}, capture the persisted row, and assert the
 * payload is the byte-identical v1 FLAT wire (TASK-BE-422/423 — top-level fields,
 * NO {@code payload}/{@code eventType}/{@code source} envelope wrapper).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxAccountEventPublisherTest {

    @Mock
    private AccountOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxAccountEventPublisher publisher() {
        return new OutboxAccountEventPublisher(outboxRepository, objectMapper,
                new AccountEventFactory(),
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private Account account(String id, AccountStatus status) {
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "user@example.com", null,
                status, Instant.now(), Instant.now(), null, null, null, 0);
    }

    private AccountOutboxJpaEntity captureRow() {
        ArgumentCaptor<AccountOutboxJpaEntity> captor = ArgumentCaptor.forClass(AccountOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("publishAccountLocked — flat payload carries a UUID eventId; row PK reuses it")
    void publishAccountLocked_flatEventId_reusedAsRowId() throws Exception {
        publisher().publishAccountLocked(
                account("acc-1", AccountStatus.LOCKED), TenantId.FAN_PLATFORM.value(),
                "ADMIN_LOCK", "operator", "op-7", Instant.parse("2026-04-14T10:00:00Z"));

        AccountOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateType()).isEqualTo("Account");
        assertThat(row.getAggregateId()).isEqualTo("acc-1");
        assertThat(row.getEventType()).isEqualTo("account.locked");
        assertThat(row.getPartitionKey()).isEqualTo("acc-1");

        JsonNode payload = objectMapper.readTree(row.getPayload());
        // FLAT — no envelope wrapper.
        assertThat(payload.has("payload")).isFalse();
        assertThat(payload.has("eventType")).isFalse();
        assertThat(payload.has("source")).isFalse();
        // account.locked carries an eventId (TASK-BE-041b); the row PK reuses it.
        String eventId = payload.get("eventId").asText();
        assertThat(row.getId()).isEqualTo(UUID.fromString(eventId));
        assertThat(payload.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(payload.get("reasonCode").asText()).isEqualTo("ADMIN_LOCK");
        assertThat(payload.get("actorId").asText()).isEqualTo("op-7");
        assertThat(payload.get("lockedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishStatusChanged — flat payload (top-level fields, no envelope), fresh row id")
    void publishStatusChanged_flatWire() throws Exception {
        publisher().publishStatusChanged(
                account("acc-1", AccountStatus.LOCKED), TenantId.FAN_PLATFORM.value(), "ACTIVE",
                "ADMIN_LOCK", "operator", "op-7", Instant.parse("2026-04-14T10:00:00Z"));

        AccountOutboxJpaEntity row = captureRow();
        JsonNode wire = objectMapper.readTree(row.getPayload());
        assertThat(wire.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(wire.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(wire.get("currentStatus").asText()).isEqualTo("LOCKED");
        assertThat(wire.get("reasonCode").asText()).isEqualTo("ADMIN_LOCK");
        assertThat(wire.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(wire.has("payload")).isFalse();
        assertThat(wire.has("eventType")).isFalse();
        // No embedded eventId on status.changed → row PK is a fresh UUID, payload carries none.
        assertThat(wire.has("eventId")).isFalse();
        assertThat(row.getId()).isNotNull();
    }

    @Test
    @DisplayName("publishAccountDeleted — flat, no eventId field (accountId+phase dedupe preserved)")
    void publishAccountDeleted_flatNoEventId() throws Exception {
        publisher().publishAccountDeleted(
                account("acc-2", AccountStatus.ACTIVE), TenantId.FAN_PLATFORM.value(),
                "USER_REQUEST", "user", "u-9",
                Instant.parse("2026-04-14T10:00:00Z"), Instant.parse("2026-05-14T10:00:00Z"));

        JsonNode wire = objectMapper.readTree(captureRow().getPayload());
        assertThat(wire.get("accountId").asText()).isEqualTo("acc-2");
        assertThat(wire.get("anonymized").asBoolean()).isFalse();
        assertThat(wire.has("eventId")).isFalse();
        assertThat(wire.has("payload")).isFalse();
    }

    @Test
    @DisplayName("tenantId null/blank → IllegalArgumentException")
    void blankTenantId_throws() {
        Account acc = account("acc-x", AccountStatus.ACTIVE);
        assertThatThrownBy(() ->
                publisher().publishAccountCreated(acc, null, "ko-KR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
        assertThatThrownBy(() ->
                publisher().publishStatusChanged(acc, "", "ACTIVE", "ADMIN_LOCK",
                        "operator", "op-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }
}
