package com.example.security.consumer;

import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEventMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Maps auth.login.succeeded envelope to LoginHistoryEntry with SUCCESS outcome")
    void mapsSucceededEventToEntry() throws Exception {
        String json = """
                {
                  "eventId": "evt-001",
                  "eventType": "auth.login.succeeded",
                  "source": "auth-service",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "schemaVersion": 1,
                  "partitionKey": "acc-123",
                  "payload": {
                    "accountId": "acc-123",
                    "ipMasked": "192.168.1.***",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "abcdef123456789012345678",
                    "geoCountry": "KR",
                    "sessionJti": "jti-001",
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;

        JsonNode envelope = objectMapper.readTree(json);
        LoginHistoryEntry entry = AuthEventMapper.toLoginHistoryEntry(envelope, LoginOutcome.SUCCESS);

        assertThat(entry.getEventId()).isEqualTo("evt-001");
        assertThat(entry.getAccountId()).isEqualTo("acc-123");
        assertThat(entry.getOutcome()).isEqualTo(LoginOutcome.SUCCESS);
        assertThat(entry.getIpMasked()).isEqualTo("192.168.1.***");
        assertThat(entry.getUserAgentFamily()).isEqualTo("Chrome 120");
        assertThat(entry.getDeviceFingerprint()).hasSize(12);
        assertThat(entry.getGeoCountry()).isEqualTo("KR");
        assertThat(entry.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Maps auth.login.failed envelope with RATE_LIMITED reason")
    void mapsFailedRateLimitedEvent() throws Exception {
        String json = """
                {
                  "eventId": "evt-002",
                  "eventType": "auth.login.failed",
                  "source": "auth-service",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "schemaVersion": 1,
                  "partitionKey": "acc-456",
                  "payload": {
                    "accountId": "acc-456",
                    "emailHash": "abc123",
                    "failureReason": "RATE_LIMITED",
                    "failCount": 6,
                    "ipMasked": "10.0.0.***",
                    "userAgentFamily": "Safari 17",
                    "deviceFingerprint": "xyz789",
                    "geoCountry": "US",
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;

        JsonNode envelope = objectMapper.readTree(json);
        LoginOutcome outcome = AuthEventMapper.resolveFailureOutcome(envelope.path("payload"));

        assertThat(outcome).isEqualTo(LoginOutcome.RATE_LIMITED);
    }

    @Test
    @DisplayName("resolveFailureOutcome returns FAILURE for non-RATE_LIMITED reasons")
    void resolveFailureOutcomeNonRateLimited() throws Exception {
        String json = """
                { "failureReason": "CREDENTIALS_INVALID" }
                """;
        JsonNode payload = objectMapper.readTree(json);
        assertThat(AuthEventMapper.resolveFailureOutcome(payload)).isEqualTo(LoginOutcome.FAILURE);
    }

    @Test
    @DisplayName("Maps event with null accountId")
    void mapsEventWithNullAccountId() throws Exception {
        String json = """
                {
                  "eventId": "evt-003",
                  "eventType": "auth.login.attempted",
                  "source": "auth-service",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "schemaVersion": 1,
                  "partitionKey": "hash-abc",
                  "payload": {
                    "accountId": null,
                    "emailHash": "hash-abc",
                    "ipMasked": "1.2.3.***",
                    "userAgentFamily": "Firefox 120",
                    "deviceFingerprint": "fp-short",
                    "geoCountry": "JP",
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;

        JsonNode envelope = objectMapper.readTree(json);
        LoginHistoryEntry entry = AuthEventMapper.toLoginHistoryEntry(envelope, LoginOutcome.ATTEMPTED);

        assertThat(entry.getAccountId()).isNull();
        assertThat(entry.getOutcome()).isEqualTo(LoginOutcome.ATTEMPTED);
    }

    // --- TASK-BE-026 (absorbed from TASK-BE-025 review): toEvaluationContext coverage
    // for the deviceId / isNewDevice fields. DeviceChangeRule relies on these being
    // mapped conservatively (null on legacy/malformed inputs) so that fingerprint-based
    // fallback remains correct.

    @Test
    @DisplayName("toEvaluationContext maps isNewDevice=true correctly")
    void toEvaluationContext_isNewDeviceTrue() throws Exception {
        String json = """
                {
                  "eventId": "evt-new-1",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "ipMasked": "1.2.*.*",
                    "deviceFingerprint": "fp-1",
                    "geoCountry": "KR",
                    "deviceId": "dev-new",
                    "isNewDevice": true,
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.deviceId()).isEqualTo("dev-new");
        assertThat(ctx.isNewDevice()).isTrue();
    }

    @Test
    @DisplayName("toEvaluationContext maps isNewDevice=false correctly")
    void toEvaluationContext_isNewDeviceFalse() throws Exception {
        String json = """
                {
                  "eventId": "evt-new-2",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "deviceId": "dev-existing",
                    "isNewDevice": false,
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.deviceId()).isEqualTo("dev-existing");
        assertThat(ctx.isNewDevice()).isFalse();
    }

    @Test
    @DisplayName("toEvaluationContext maps legacy event (isNewDevice absent) to null")
    void toEvaluationContext_legacyEventIsNewDeviceAbsent() throws Exception {
        String json = """
                {
                  "eventId": "evt-legacy",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "deviceFingerprint": "fp-legacy",
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.deviceId()).isNull();
        assertThat(ctx.isNewDevice()).isNull();
    }

    @Test
    @DisplayName("toEvaluationContext maps isNewDevice=null explicit to null")
    void toEvaluationContext_isNewDeviceExplicitNull() throws Exception {
        String json = """
                {
                  "eventId": "evt-null",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "deviceId": null,
                    "isNewDevice": null,
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.deviceId()).isNull();
        assertThat(ctx.isNewDevice()).isNull();
    }

    @Test
    @DisplayName("toEvaluationContext maps malformed isNewDevice (integer) to null — fingerprint fallback")
    void toEvaluationContext_isNewDeviceMalformedInteger() throws Exception {
        String json = """
                {
                  "eventId": "evt-bad-int",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "deviceFingerprint": "fp-1",
                    "deviceId": "dev-1",
                    "isNewDevice": 1,
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.isNewDevice()).isNull();
    }

    @Test
    @DisplayName("toEvaluationContext maps malformed isNewDevice (string) to null — fingerprint fallback")
    void toEvaluationContext_isNewDeviceMalformedString() throws Exception {
        String json = """
                {
                  "eventId": "evt-bad-str",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": {
                    "accountId": "acc-1",
                    "deviceFingerprint": "fp-1",
                    "deviceId": "dev-1",
                    "isNewDevice": "true",
                    "timestamp": "2026-04-12T10:00:00Z"
                  }
                }
                """;
        JsonNode envelope = objectMapper.readTree(json);
        EvaluationContext ctx = AuthEventMapper.toEvaluationContext(envelope, "auth.login.succeeded");

        assertThat(ctx.isNewDevice()).isNull();
    }

    @Test
    @DisplayName("toEvaluationContext maps deviceId absent to null, deviceId present passes through")
    void toEvaluationContext_deviceIdAbsentVsPresent() throws Exception {
        String absentJson = """
                {
                  "eventId": "evt-no-dev",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": { "accountId": "acc-1", "timestamp": "2026-04-12T10:00:00Z" }
                }
                """;
        String presentJson = """
                {
                  "eventId": "evt-has-dev",
                  "eventType": "auth.login.succeeded",
                  "occurredAt": "2026-04-12T10:00:00Z",
                  "payload": { "accountId": "acc-1", "deviceId": "dev-xyz", "timestamp": "2026-04-12T10:00:00Z" }
                }
                """;

        EvaluationContext absent = AuthEventMapper.toEvaluationContext(
                objectMapper.readTree(absentJson), "auth.login.succeeded");
        EvaluationContext present = AuthEventMapper.toEvaluationContext(
                objectMapper.readTree(presentJson), "auth.login.succeeded");

        assertThat(absent.deviceId()).isNull();
        assertThat(present.deviceId()).isEqualTo("dev-xyz");
    }
}
