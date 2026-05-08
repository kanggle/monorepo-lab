package com.wms.admin.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies the {@code X-Read-Model-Lag-Seconds} header gating contract. */
class ReadModelLagHeaderInterceptorTest {

    private MeterRegistry registry;
    private ReadModelLagHeaderInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        interceptor = new ReadModelLagHeaderInterceptor(registry, 5.0d);
    }

    @Test
    void emptyRegistry_omitsHeader() {
        HttpServletResponse response = invoke();
        assertThat(response.getHeader("X-Read-Model-Lag-Seconds")).isNull();
    }

    @Test
    void belowThreshold_omitsHeader() {
        recordLag("wms.inventory.adjusted.v1", Duration.ofMillis(2_400));

        HttpServletResponse response = invoke();
        assertThat(response.getHeader("X-Read-Model-Lag-Seconds")).isNull();
    }

    @Test
    void aboveThreshold_emitsHeaderWithMaxAcrossTopics() {
        recordLag("wms.inventory.adjusted.v1", Duration.ofMillis(2_400));
        recordLag("wms.inbound.asn.v1", Duration.ofMillis(7_500));

        HttpServletResponse response = invoke();
        assertThat(response.getHeader("X-Read-Model-Lag-Seconds")).isEqualTo("7.5");
    }

    @Test
    void belowThreshold_omitsHeader_evenWhenManyTopicsRecord() {
        recordLag("wms.inventory.adjusted.v1", Duration.ofMillis(1_000));
        recordLag("wms.inbound.asn.v1", Duration.ofMillis(2_000));
        recordLag("wms.outbound.order.v1", Duration.ofMillis(4_900));

        HttpServletResponse response = invoke();
        assertThat(response.getHeader("X-Read-Model-Lag-Seconds")).isNull();
    }

    private void recordLag(String topic, Duration value) {
        Timer.builder(ProjectionMetrics.LAG_METRIC)
                .tags(Tags.of("source_service", "test", "topic", topic))
                .register(registry)
                .record(value);
    }

    private HttpServletResponse invoke() {
        HttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.postHandle(request, response, new Object(), null);
        return response;
    }
}
