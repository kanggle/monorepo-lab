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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link AccountEventPublisher}.
 *
 * <p>TASK-BE-041b-fix: the flat {@code account.locked} payload must carry an
 * {@code eventId} (UUID) so downstream consumers can idempotently deduplicate
 * Kafka at-least-once redeliveries.
 *
 * <p>TASK-BE-248: all publish* methods require a non-null, non-blank
 * {@code tenantId}; a blank value throws {@link IllegalArgumentException}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private AccountEventFactory factory = new AccountEventFactory();

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
                account("acc-1"), TenantId.FAN_PLATFORM.value(), "ADMIN_LOCK", "operator", "op-7",
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
        publisher.publishAccountLocked(account("acc-1"), TenantId.FAN_PLATFORM.value(),
                "ADMIN_LOCK", "operator", "op-1",
                Instant.parse("2026-04-14T10:00:00Z"));
        publisher.publishAccountLocked(account("acc-2"), TenantId.FAN_PLATFORM.value(),
                "AUTO_DETECT", "system", null,
                Instant.parse("2026-04-14T10:00:01Z"));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter, org.mockito.Mockito.times(2))
                .save(any(), any(), eq("account.locked"), captor.capture());

        String e1 = objectMapper.readTree(captor.getAllValues().get(0)).get("eventId").asText();
        String e2 = objectMapper.readTree(captor.getAllValues().get(1)).get("eventId").asText();
        assertThat(e1).isNotEqualTo(e2);
    }

    // ── TASK-BE-423: producer-side FLAT-wire guard for the BE-422 events ───────────
    // The exact JSON captured here is what `saveEvent` writes to the outbox and what
    // `OutboxPublisher` relays to Kafka verbatim — i.e. the real on-wire message. These
    // lock the flat shape (top-level fields, NO `payload` envelope wrapper) so the
    // producer can never silently regress to a nested envelope and re-break the ecommerce
    // account.* consumers fixed in TASK-BE-422 (see account-lifecycle-subscriptions.md
    // § Envelope). The consumer-side flat deserialization tests meet this exact shape.

    private Account lockedAccount(String id) {
        return Account.reconstitute(id, TenantId.FAN_PLATFORM, "user@example.com", null,
                AccountStatus.LOCKED, Instant.now(), Instant.now(), null, null, null, 0);
    }

    @Test
    @DisplayName("publishStatusChanged — outbox JSON is FLAT (top-level fields, no payload wrapper)")
    void publishStatusChanged_flatWireShape() throws Exception {
        publisher.publishStatusChanged(
                lockedAccount("acc-1"), TenantId.FAN_PLATFORM.value(), "ACTIVE",
                "ADMIN_LOCK", "operator", "op-7", Instant.parse("2026-04-14T10:00:00Z"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Account"), eq("acc-1"), eq("account.status.changed"),
                payloadCaptor.capture());

        JsonNode wire = objectMapper.readTree(payloadCaptor.getValue());
        // FLAT: the consumer-relevant fields are at the JSON root.
        assertThat(wire.get("accountId").asText()).isEqualTo("acc-1");
        assertThat(wire.get("tenantId").asText()).isEqualTo(TenantId.FAN_PLATFORM.value());
        assertThat(wire.get("previousStatus").asText()).isEqualTo("ACTIVE");
        assertThat(wire.get("currentStatus").asText()).isEqualTo("LOCKED");
        assertThat(wire.get("reasonCode").asText()).isEqualTo("ADMIN_LOCK");
        assertThat(wire.get("actorType").asText()).isEqualTo("operator");
        assertThat(wire.get("actorId").asText()).isEqualTo("op-7");
        assertThat(wire.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        // NOT a nested envelope — these would break the flat consumer DTO.
        assertThat(wire.has("payload")).as("no nested payload wrapper").isFalse();
        assertThat(wire.has("eventType")).as("no eventType envelope field").isFalse();
        assertThat(wire.has("source")).as("no source envelope field").isFalse();
    }

    @Test
    @DisplayName("publishAccountDeleted — outbox JSON is FLAT (top-level fields, no payload wrapper)")
    void publishAccountDeleted_flatWireShape() throws Exception {
        publisher.publishAccountDeleted(
                account("acc-2"), TenantId.FAN_PLATFORM.value(), "USER_REQUEST", "user", "u-9",
                Instant.parse("2026-04-14T10:00:00Z"), Instant.parse("2026-05-14T10:00:00Z"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("Account"), eq("acc-2"), eq("account.deleted"),
                payloadCaptor.capture());

        JsonNode wire = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(wire.get("accountId").asText()).isEqualTo("acc-2");
        assertThat(wire.get("tenantId").asText()).isEqualTo(TenantId.FAN_PLATFORM.value());
        assertThat(wire.get("reasonCode").asText()).isEqualTo("USER_REQUEST");
        assertThat(wire.get("actorType").asText()).isEqualTo("user");
        assertThat(wire.get("actorId").asText()).isEqualTo("u-9");
        assertThat(wire.get("deletedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
        assertThat(wire.get("gracePeriodEndsAt").asText()).isEqualTo("2026-05-14T10:00:00Z");
        assertThat(wire.get("anonymized").asBoolean()).isFalse();
        // account.deleted carries NO eventId on the flat wire (only account.locked does) —
        // this is why TASK-BE-422 re-keyed the order-service dedup off accountId+phase.
        assertThat(wire.has("eventId")).as("account.deleted has no eventId field").isFalse();
        assertThat(wire.has("payload")).as("no nested payload wrapper").isFalse();
    }

    // ── TASK-BE-248: tenantId null/blank guard ────────────────────────────────

    @Test
    @DisplayName("publishAccountLocked — tenantId null → IllegalArgumentException")
    void publishAccountLocked_nullTenantId_throwsIllegalArgument() {
        Account acc = account("acc-null");
        assertThatThrownBy(() ->
                publisher.publishAccountLocked(acc, null, "ADMIN_LOCK", "operator", "op-1",
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }

    @Test
    @DisplayName("publishAccountLocked — tenantId blank → IllegalArgumentException")
    void publishAccountLocked_blankTenantId_throwsIllegalArgument() {
        Account acc = account("acc-blank");
        assertThatThrownBy(() ->
                publisher.publishAccountLocked(acc, "   ", "ADMIN_LOCK", "operator", "op-1",
                        Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }

    @Test
    @DisplayName("publishAccountCreated — tenantId null → IllegalArgumentException")
    void publishAccountCreated_nullTenantId_throwsIllegalArgument() {
        Account acc = account("acc-c");
        assertThatThrownBy(() ->
                publisher.publishAccountCreated(acc, null, "ko-KR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }

    @Test
    @DisplayName("publishStatusChanged — tenantId blank → IllegalArgumentException")
    void publishStatusChanged_blankTenantId_throwsIllegalArgument() {
        Account acc = account("acc-s");
        assertThatThrownBy(() ->
                publisher.publishStatusChanged(acc, "", "ACTIVE", "ADMIN_LOCK",
                        "operator", "op-1", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tenantId required");
    }
}
