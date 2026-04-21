package com.example.user.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserMetrics 단위 테스트")
class UserMetricsTest {

    private SimpleMeterRegistry registry;
    private UserMetrics userMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        userMetrics = new UserMetrics(registry);
    }

    @Test
    @DisplayName("이벤트 발행 실패 시 카운터가 증가한다")
    void incrementEventPublishFailure_incrementsCounter() {
        userMetrics.incrementEventPublishFailure("UserProfileUpdated");

        Counter counter = registry.find("event_publish_failure_total")
                .tag("service", "user-service")
                .tag("event_type", "UserProfileUpdated")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("같은 이벤트 타입으로 여러 번 호출하면 카운터가 누적된다")
    void incrementEventPublishFailure_multipleIncrements_accumulates() {
        userMetrics.incrementEventPublishFailure("UserWithdrawn");
        userMetrics.incrementEventPublishFailure("UserWithdrawn");
        userMetrics.incrementEventPublishFailure("UserWithdrawn");

        Counter counter = registry.find("event_publish_failure_total")
                .tag("service", "user-service")
                .tag("event_type", "UserWithdrawn")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("다른 이벤트 타입은 별도의 카운터로 집계된다")
    void incrementEventPublishFailure_differentEventTypes_separateCounters() {
        userMetrics.incrementEventPublishFailure("UserProfileUpdated");
        userMetrics.incrementEventPublishFailure("UserWithdrawn");

        Counter profileCounter = registry.find("event_publish_failure_total")
                .tag("event_type", "UserProfileUpdated")
                .counter();
        Counter withdrawnCounter = registry.find("event_publish_failure_total")
                .tag("event_type", "UserWithdrawn")
                .counter();

        assertThat(profileCounter).isNotNull();
        assertThat(profileCounter.count()).isEqualTo(1.0);
        assertThat(withdrawnCounter).isNotNull();
        assertThat(withdrawnCounter.count()).isEqualTo(1.0);
    }
}
