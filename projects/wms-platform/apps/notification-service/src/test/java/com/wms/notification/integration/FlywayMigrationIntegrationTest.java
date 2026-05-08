package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.adapter.outbound.persistence.jpa.routing.RoutingRuleJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Smoke integration: Flyway migrations run cleanly against Postgres 16 and
 * the seeded routing rules are queryable. Implicitly validates ddl-auto
 * column metadata against the SQL — any drift surfaces here.
 */
class FlywayMigrationIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    RoutingRuleJpaRepository routingRuleRepository;

    @Test
    void seededRoutingRulesPresent() {
        assertThat(routingRuleRepository.count()).isEqualTo(6);
        assertThat(routingRuleRepository.findByEventTypeAndEnabledTrue("inventory.low-stock-detected"))
                .hasSize(1);
        assertThat(routingRuleRepository.findByEventTypeAndEnabledTrue("outbound.shipping.confirmed"))
                .hasSize(1);
    }
}
