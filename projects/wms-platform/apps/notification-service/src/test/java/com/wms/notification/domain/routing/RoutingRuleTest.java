package com.wms.notification.domain.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.alert.AlertSeverity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoutingRuleTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void enabledRuleMatchesEventTypeAndPredicate() {
        RoutingRule rule = new RoutingRule(
                UUID.randomUUID(),
                "inventory.adjusted",
                new RoutingMatcher.PayloadPredicateMatch(
                        "$.payload.delta", RoutingMatcher.Op.ABS_GTE, 100),
                List.of(slack("wms-alerts")),
                AlertSeverity.INFO,
                true,
                NOW, NOW);
        assertThat(rule.matches(envelope("inventory.adjusted", Map.of("delta", 200)))).isTrue();
        assertThat(rule.matches(envelope("inventory.adjusted", Map.of("delta", 50)))).isFalse();
        assertThat(rule.matches(envelope("inventory.other", Map.of("delta", 200)))).isFalse();
    }

    @Test
    void disabledRuleNeverMatches() {
        RoutingRule rule = new RoutingRule(
                UUID.randomUUID(),
                "inventory.low-stock-detected",
                new RoutingMatcher.AlwaysMatch(),
                List.of(slack("wms-alerts")),
                AlertSeverity.WARNING,
                false,
                NOW, NOW);
        assertThat(rule.matches(envelope("inventory.low-stock-detected", Map.of()))).isFalse();
    }

    @Test
    void enabledRuleRequiresAtLeastOneChannelTarget() {
        assertThatThrownBy(() -> new RoutingRule(
                UUID.randomUUID(), "x", new RoutingMatcher.AlwaysMatch(),
                List.of(), AlertSeverity.INFO, true, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channelTarget");
    }

    @Test
    void disabledRuleMayHaveEmptyChannelTargets() {
        // Operator may temporarily empty the list while editing; not ideal,
        // but we don't reject it because the rule is inert.
        new RoutingRule(UUID.randomUUID(), "x", new RoutingMatcher.AlwaysMatch(),
                List.of(), AlertSeverity.INFO, false, NOW, NOW);
    }

    private static ChannelTarget slack(String alias) {
        return new ChannelTarget(ChannelType.SLACK, alias, "template");
    }

    private static AlertEnvelope envelope(String type, Map<String, Object> payload) {
        return new AlertEnvelope(UUID.randomUUID(), type, "wms.x.v1", "agg", payload, NOW);
    }
}
