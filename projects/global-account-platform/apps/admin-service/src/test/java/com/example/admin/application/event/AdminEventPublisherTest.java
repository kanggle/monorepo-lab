package com.example.admin.application.event;

import com.example.admin.application.Outcome;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdminEventPublisher} focused on the edge-case envelope
 * behavior required by the spec but not yet covered by
 * {@link AdminEventPublisherCanonicalEnvelopeTest}:
 *
 * <ul>
 *   <li>{@code targetId == null} → outbox aggregateId falls back to {@code "-"}
 *       and the payload {@code target.id}/{@code target.displayHint} are null.</li>
 *   <li>{@code targetType == "ACCOUNT"} with a non-email targetId (UUID-like) →
 *       {@code displayHint} is null (never leak raw account IDs).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AdminEventPublisher 단위 테스트")
class AdminEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminEventPublisher publisher;

    @Test
    @DisplayName("publishAdminActionPerformed_nullTargetId_aggregateIdFallsBackToDash")
    void publishAdminActionPerformed_nullTargetId_aggregateIdFallsBackToDash() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-9",
                "jti-9",
                "audit.query",
                "/api/admin/audit/queries",
                "GET",
                "AUDIT_QUERY",
                null,
                Outcome.SUCCESS,
                null,
                Instant.parse("2026-04-14T10:00:00Z"));

        publisher.publishAdminActionPerformed(env);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("AdminAction"),
                eq("-"),
                eq("admin.action.performed"),
                payloadCaptor.capture());

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        JsonNode target = root.get("target");
        assertThat(target.get("type").asText()).isEqualTo("AUDIT_QUERY");
        assertThat(target.get("id").isNull()).isTrue();
        assertThat(target.get("displayHint").isNull()).isTrue();
        assertThat(root.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishAdminActionPerformed_accountTargetWithUuidId_displayHintIsNull")
    void publishAdminActionPerformed_accountTargetWithUuidId_displayHintIsNull() throws Exception {
        // ACCOUNT target with a non-email (UUID-like) id: displayHint must remain
        // null per the publisher's PII rule — UUIDs are not PII and must not be
        // duplicated into displayHint (rules/traits/regulated.md R4).
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1",
                "jti-1",
                "account.lock",
                "/api/admin/accounts/0191c4e8-7e10-7a73-9d4f-1a2b3c4d5e6f/lock",
                "POST",
                "ACCOUNT",
                "0191c4e8-7e10-7a73-9d4f-1a2b3c4d5e6f",
                Outcome.SUCCESS,
                null,
                Instant.parse("2026-04-14T10:00:00Z"));

        publisher.publishAdminActionPerformed(env);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("AdminAction"),
                eq("0191c4e8-7e10-7a73-9d4f-1a2b3c4d5e6f"),
                eq("admin.action.performed"),
                payloadCaptor.capture());

        JsonNode target = objectMapper.readTree(payloadCaptor.getValue()).get("target");
        assertThat(target.get("type").asText()).isEqualTo("ACCOUNT");
        assertThat(target.get("id").asText()).isEqualTo("0191c4e8-7e10-7a73-9d4f-1a2b3c4d5e6f");
        assertThat(target.get("displayHint").isNull())
                .as("UUID account ids are not PII; displayHint must stay null")
                .isTrue();
    }
}
