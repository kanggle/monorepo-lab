package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.outbound.RoutingRuleRepository;
import com.wms.notification.domain.routing.RoutingMatcher;
import com.wms.notification.domain.routing.RoutingRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Round-trip the seeded JSONB matchers + channel targets through the
 * persistence adapter — proves the codec correctly decodes the discriminator
 * union from the DB rows V2 just inserted.
 */
class RoutingRulePersistenceIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    RoutingRuleRepository routingRuleRepository;

    @Test
    void inventoryAlertRuleDecodesAsAlwaysMatch() {
        List<RoutingRule> rules =
                routingRuleRepository.findEnabledByEventType("inventory.low-stock-detected");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).matcher()).isInstanceOf(RoutingMatcher.AlwaysMatch.class);
        assertThat(rules.get(0).channelTargets()).hasSize(1);
        assertThat(rules.get(0).channelTargets().get(0).channelId()).isEqualTo("wms-alerts");
    }

    @Test
    void inventoryAdjustedRuleDecodesAsPayloadPredicate() {
        List<RoutingRule> rules =
                routingRuleRepository.findEnabledByEventType("inventory.adjusted");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).matcher())
                .isInstanceOfSatisfying(RoutingMatcher.PayloadPredicateMatch.class, m -> {
                    assertThat(m.jsonPath()).isEqualTo("$.payload.delta");
                    assertThat(m.op()).isEqualTo(RoutingMatcher.Op.ABS_GTE);
                });
    }

    @Test
    void outboundOrderCancelledRuleDecodesAsInPredicate() {
        List<RoutingRule> rules =
                routingRuleRepository.findEnabledByEventType("outbound.order.cancelled");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).matcher())
                .isInstanceOfSatisfying(RoutingMatcher.PayloadPredicateMatch.class, m -> {
                    assertThat(m.op()).isEqualTo(RoutingMatcher.Op.IN);
                    assertThat(m.value()).isInstanceOf(List.class);
                });
    }
}
