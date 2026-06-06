package com.example.admin.application.event;

import com.example.admin.application.Outcome;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Slice test verifying the {@code admin.action.performed} outbox payload
 * conforms to the canonical envelope declared in data-model.md:
 *   eventId + occurredAt + actor{type,id,sessionId} + action{permission,endpoint,method}
 *   + target{type,id,displayHint} + outcome + reason.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AdminEventPublisherCanonicalEnvelopeTest {

    @Mock
    OutboxWriter outboxWriter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AdminEventPublisher publisher() {
        return new AdminEventPublisher(outboxWriter, objectMapper);
    }

    @Test
    void envelope_contains_all_canonical_fields_in_order() throws Exception {
        Instant occurredAt = Instant.parse("2026-04-14T10:00:00.123Z");
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1",
                "jti-xyz",
                "account.lock",
                "/api/admin/accounts/acc-1/lock",
                "POST",
                "ACCOUNT",
                "acc-1",
                Outcome.SUCCESS,
                null,
                occurredAt);

        publisher().publishAdminActionPerformed(env);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("AdminAction"), eq("acc-1"),
                eq("admin.action.performed"),
                payloadCaptor.capture());

        JsonNode root = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(root.has("eventId")).isTrue();
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00.123Z");

        assertThat(root.get("actor").get("type").asText()).isEqualTo("operator");
        assertThat(root.get("actor").get("id").asText()).isEqualTo("op-uuid-1");
        assertThat(root.get("actor").get("sessionId").asText()).isEqualTo("jti-xyz");

        assertThat(root.get("action").get("permission").asText()).isEqualTo("account.lock");
        assertThat(root.get("action").get("endpoint").asText()).isEqualTo("/api/admin/accounts/acc-1/lock");
        assertThat(root.get("action").get("method").asText()).isEqualTo("POST");

        assertThat(root.get("target").get("type").asText()).isEqualTo("ACCOUNT");
        assertThat(root.get("target").get("id").asText()).isEqualTo("acc-1");
        // UUID-like account id is not PII → displayHint is null (never raw leakage).
        assertThat(root.get("target").get("displayHint").isNull()).isTrue();

        assertThat(root.get("outcome").asText()).isEqualTo("SUCCESS");
        assertThat(root.get("reason").isNull()).isTrue();
    }

    @Test
    void envelope_email_target_id_produces_masked_displayHint() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1",
                null,
                "account.lock",
                "/api/admin/accounts/jane.doe@example.com/lock",
                "POST",
                "ACCOUNT",
                "jane.doe@example.com",
                Outcome.DENIED,
                "PERMISSION_NOT_GRANTED",
                Instant.now());

        publisher().publishAdminActionPerformed(env);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("AdminAction"), eq("jane.doe@example.com"),
                eq("admin.action.performed"), payloadCaptor.capture());

        JsonNode target = objectMapper.readTree(payloadCaptor.getValue()).get("target");
        assertThat(target.get("displayHint").asText()).isEqualTo("j***@example.com");
    }

    @Test
    void envelope_session_target_never_exposes_displayHint() throws Exception {
        AdminEventPublisher.Envelope env = new AdminEventPublisher.Envelope(
                "op-uuid-1",
                "jti-1",
                "account.force_logout",
                "/api/admin/sessions/acc-1/revoke",
                "POST",
                "SESSION",
                "acc-1",
                Outcome.SUCCESS,
                null,
                Instant.now());

        publisher().publishAdminActionPerformed(env);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(eq("AdminAction"), eq("acc-1"),
                eq("admin.action.performed"), payloadCaptor.capture());

        JsonNode target = objectMapper.readTree(payloadCaptor.getValue()).get("target");
        assertThat(target.get("displayHint").isNull()).isTrue();
    }
}
