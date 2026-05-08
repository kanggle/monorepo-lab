package com.wms.notification.domain.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.alert.AlertSeverity;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutingMatcherTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void alwaysMatchPasses() {
        assertThat(new RoutingMatcher.AlwaysMatch().matches(envelope(Map.of()))).isTrue();
    }

    @Test
    void payloadPredicateAbsGteMatches() {
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.delta", RoutingMatcher.Op.ABS_GTE, 100);
        assertThat(m.matches(envelopePayload(Map.of("delta", 150)))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("delta", -200)))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("delta", 99)))).isFalse();
        assertThat(m.matches(envelopePayload(Map.of("delta", -50)))).isFalse();
    }

    @Test
    void payloadPredicateGtMatches() {
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.discrepancyCount", RoutingMatcher.Op.GT, 0);
        assertThat(m.matches(envelopePayload(Map.of("discrepancyCount", 1)))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("discrepancyCount", 0)))).isFalse();
    }

    @Test
    void payloadPredicateInMatches() {
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.priorStatus", RoutingMatcher.Op.IN,
                List.of("PICKED", "PACKED", "SHIPPED"));
        assertThat(m.matches(envelopePayload(Map.of("priorStatus", "PICKED")))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("priorStatus", "PACKED")))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("priorStatus", "RECEIVED")))).isFalse();
    }

    @Test
    void payloadPredicateMissingFieldFails() {
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.missing", RoutingMatcher.Op.GT, 0);
        assertThat(m.matches(envelopePayload(Map.of("present", 1)))).isFalse();
    }

    @Test
    void severityThresholdMatches() {
        RoutingMatcher m = new RoutingMatcher.SeverityThresholdMatch(AlertSeverity.WARNING);
        assertThat(m.matches(envelopePayload(Map.of("severity", "WARNING")))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("severity", "CRITICAL")))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("severity", "INFO")))).isFalse();
    }

    @Test
    void severityThresholdHandlesUnknownGracefully() {
        RoutingMatcher m = new RoutingMatcher.SeverityThresholdMatch(AlertSeverity.WARNING);
        assertThat(m.matches(envelopePayload(Map.of("severity", "BANANA")))).isFalse();
        assertThat(m.matches(envelopePayload(Map.of()))).isFalse();
    }

    @Test
    void payloadPredicateTraversesNestedMaps() {
        Map<String, Object> nested = new HashMap<>();
        nested.put("inner", Map.of("value", 250));
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.inner.value", RoutingMatcher.Op.ABS_GTE, 100);
        assertThat(m.matches(envelopePayload(nested))).isTrue();
    }

    @Test
    void payloadPredicateEqStringComparesAsString() {
        RoutingMatcher m = new RoutingMatcher.PayloadPredicateMatch(
                "$.payload.warehouseId", RoutingMatcher.Op.EQ, "WH-1");
        assertThat(m.matches(envelopePayload(Map.of("warehouseId", "WH-1")))).isTrue();
        assertThat(m.matches(envelopePayload(Map.of("warehouseId", "WH-2")))).isFalse();
    }

    private static AlertEnvelope envelope(Map<String, Object> payload) {
        return new AlertEnvelope(UUID.randomUUID(), "x.y.z", "wms.foo.v1", "agg-1", payload, NOW);
    }

    private static AlertEnvelope envelopePayload(Map<String, Object> payload) {
        return envelope(payload);
    }
}
