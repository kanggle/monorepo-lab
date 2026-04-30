package com.example.messaging.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OutboxMetricsAutoConfiguration 단위 테스트")
class OutboxMetricsAutoConfigurationTest {

    private OutboxFailureHandler handlerFor(String appName, SimpleMeterRegistry registry) {
        OutboxMetricsAutoConfiguration config = new OutboxMetricsAutoConfiguration();
        return config.defaultOutboxFailureHandler(registry, appName);
    }

    @Test
    @DisplayName("단일 publish 실패 시 event_type 태그와 함께 카운터가 1 증가한다")
    void singleFailure_incrementsCounterTaggedWithEventType() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxFailureHandler handler = handlerFor("auth-service", registry);

        handler.onFailure("auth.login.succeeded", "acc-1", new RuntimeException("boom"));

        Counter counter = registry.find("auth_outbox_publish_failures")
                .tag("event_type", "auth.login.succeeded")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("여러 event_type으로 실패 시 태그별 독립 카운터가 각각 증가한다")
    void multipleEventTypes_registersSeparateCountersPerTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxFailureHandler handler = handlerFor("account-service", registry);

        handler.onFailure("account.created", "acc-1", new RuntimeException("boom-1"));
        handler.onFailure("account.created", "acc-2", new RuntimeException("boom-2"));
        handler.onFailure("account.status.changed", "acc-3", new RuntimeException("boom-3"));

        Counter created = registry.find("account_outbox_publish_failures")
                .tag("event_type", "account.created").counter();
        Counter changed = registry.find("account_outbox_publish_failures")
                .tag("event_type", "account.status.changed").counter();

        assertThat(created).isNotNull();
        assertThat(created.count()).isEqualTo(2.0d);
        assertThat(changed).isNotNull();
        assertThat(changed.count()).isEqualTo(1.0d);
    }

    @ParameterizedTest(name = "{0} → metric prefix: {1}")
    @DisplayName("spring.application.name에서 metric prefix를 올바르게 도출한다")
    @CsvSource({
            "auth-service,       auth",
            "account-service,    account",
            "admin-service,      admin",
            "membership-service, membership",
            "security-service,   security",
            "community-service,  community",
            "application,        application"
    })
    void appName_derivesCorrectMetricPrefix(String appName, String expectedPrefix) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxFailureHandler handler = handlerFor(appName.trim(), registry);

        handler.onFailure("some.event", "agg-1", new RuntimeException("boom"));

        Counter counter = registry.find(expectedPrefix.trim() + "_outbox_publish_failures")
                .tag("event_type", "some.event")
                .counter();
        assertThat(counter)
                .as("expected metric '%s_outbox_publish_failures' for app name '%s'",
                        expectedPrefix.trim(), appName.trim())
                .isNotNull();
    }
}
