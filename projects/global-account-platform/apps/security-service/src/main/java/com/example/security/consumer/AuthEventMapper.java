package com.example.security.consumer;

import com.example.security.domain.Tenants;
import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.gap.security.pii.PiiMaskingUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Maps auth event envelope JSON to LoginHistoryEntry domain objects.
 * Stateless utility - all methods are static.
 */
public final class AuthEventMapper {

    private AuthEventMapper() {
    }

    public static LoginHistoryEntry toLoginHistoryEntry(JsonNode envelope, LoginOutcome outcome) {
        String eventId = envelope.path("eventId").asText();
        JsonNode payload = envelope.path("payload");

        String accountId = nullableText(payload, "accountId");
        String ipMasked = nullableText(payload, "ipMasked");
        String userAgentFamily = nullableText(payload, "userAgentFamily");
        String deviceFingerprint = PiiMaskingUtils.truncateFingerprint(nullableText(payload, "deviceFingerprint"));
        String geoCountry = nullableText(payload, "geoCountry");

        String timestampStr = nullableText(payload, "timestamp");
        Instant occurredAt;
        if (timestampStr != null) {
            occurredAt = Instant.parse(timestampStr);
        } else {
            occurredAt = Instant.parse(envelope.path("occurredAt").asText());
        }

        // TASK-BE-248 Phase 2a: AbstractAuthEventConsumer.processEvent strictly rejects events
        // without tenant_id before reaching this mapper. The firstNonBlank chain is defensive
        // dead code in production; kept for structural robustness and test isolation.
        String tenantId = firstNonBlank(
                nullableText(envelope, "tenantId"),
                nullableText(payload, "tenantId"),
                Tenants.DEFAULT_TENANT_ID);

        return new LoginHistoryEntry(
                tenantId, eventId, accountId, outcome,
                ipMasked, userAgentFamily, deviceFingerprint, geoCountry,
                occurredAt
        );
    }

    /**
     * Determine outcome from auth.login.failed payload.
     */
    public static LoginOutcome resolveFailureOutcome(JsonNode payload) {
        String reason = nullableText(payload, "failureReason");
        if ("RATE_LIMITED".equals(reason)) {
            return LoginOutcome.RATE_LIMITED;
        }
        return LoginOutcome.FAILURE;
    }

    /**
     * Build an {@link EvaluationContext} for the detection pipeline.
     * Keeps the rule layer free of Kafka/JSON concerns.
     */
    public static EvaluationContext toEvaluationContext(JsonNode envelope, String eventType) {
        JsonNode payload = envelope.path("payload");
        String eventId = envelope.path("eventId").asText();
        String accountId = nullableText(payload, "accountId");
        String ipMasked = nullableText(payload, "ipMasked");
        String deviceFingerprint = nullableText(payload, "deviceFingerprint");
        String geoCountry = nullableText(payload, "geoCountry");

        String ts = nullableText(payload, "timestamp");
        Instant occurredAt;
        if (ts != null) {
            occurredAt = Instant.parse(ts);
        } else {
            String env = envelope.path("occurredAt").asText();
            occurredAt = env.isEmpty() ? Instant.now() : Instant.parse(env);
        }

        Integer failCount = null;
        JsonNode fc = payload.path("failCount");
        if (!fc.isMissingNode() && fc.isNumber()) {
            failCount = fc.asInt();
        }

        // TASK-BE-025: auth.login.succeeded now carries deviceId + isNewDevice. Legacy
        // events (pre-TASK-BE-025) omit both — DeviceChangeRule falls back to fingerprint.
        String deviceId = nullableText(payload, "deviceId");
        Boolean isNewDevice = null;
        JsonNode ind = payload.path("isNewDevice");
        if (!ind.isMissingNode() && !ind.isNull() && ind.isBoolean()) {
            isNewDevice = ind.asBoolean();
        }

        // TASK-BE-248 Phase 2a: AbstractAuthEventConsumer.processEvent strictly rejects events
        // without tenant_id before reaching this mapper. The firstNonBlank chain is defensive
        // dead code in production; kept for structural robustness and test isolation.
        String tenantId = firstNonBlank(
                nullableText(envelope, "tenantId"),
                nullableText(payload, "tenantId"),
                Tenants.DEFAULT_TENANT_ID);

        return new EvaluationContext(
                tenantId, eventId, eventType, accountId, ipMasked, deviceFingerprint, geoCountry,
                occurredAt, failCount, deviceId, isNewDevice);
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
