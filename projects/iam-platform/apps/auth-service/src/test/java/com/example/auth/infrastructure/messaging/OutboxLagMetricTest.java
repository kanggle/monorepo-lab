package com.example.auth.infrastructure.messaging;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** TASK-BE-663 — {@code auth_outbox_lag_seconds} reads the v2 table (mirrors account-service's test). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboxLagMetricTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    SimpleMeterRegistry meterRegistry;
    OutboxLagMetric metric;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metric = new OutboxLagMetric(jdbcTemplate, meterRegistry);
        metric.registerMetric();
    }

    private double gauge() {
        return meterRegistry.get(OutboxLagMetric.METRIC_NAME).gauge().value();
    }

    @Test
    @DisplayName("미발행 행 없음 → 0 (COALESCE 결과)")
    void noUnpublishedRows_reportsZero() {
        given(jdbcTemplate.queryForObject(OutboxLagMetric.LAG_SQL, Double.class)).willReturn(0.0);

        assertThat(gauge()).isZero();
    }

    @Test
    @DisplayName("오래된 미발행 행 하나 → 그 나이(초) > 0")
    void oneOldUnpublishedRow_reportsItsAge() {
        given(jdbcTemplate.queryForObject(OutboxLagMetric.LAG_SQL, Double.class)).willReturn(125.0);

        assertThat(gauge()).isEqualTo(125.0).isPositive();
    }

    @Test
    @DisplayName("쿼리가 null 을 돌려줘도 0")
    void nullResult_reportsZero() {
        given(jdbcTemplate.queryForObject(OutboxLagMetric.LAG_SQL, Double.class)).willReturn(null);

        assertThat(gauge()).isZero();
    }

    @Test
    @DisplayName("DataAccessException → -1 (측정 불가 표식)")
    void dataAccessFailure_reportsMinusOne() {
        given(jdbcTemplate.queryForObject(OutboxLagMetric.LAG_SQL, Double.class))
                .willThrow(new DataAccessResourceFailureException("db down"));

        assertThat(gauge()).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("v2 auth_outbox 의 published_at IS NULL 을 UTC 로 읽는다 — v1 outbox/status 가 아니다")
    void query_targetsV2TableNotV1() {
        assertThat(OutboxLagMetric.LAG_SQL)
                .contains("FROM auth_outbox ")
                .contains("published_at IS NULL")
                .contains("UTC_TIMESTAMP(6)")
                .doesNotContain("status")
                .doesNotContain("NOW()")
                .doesNotContain("FROM outbox ");
    }
}
