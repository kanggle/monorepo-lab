package com.wms.admin.infra.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.admin.application.projection.fakes.InMemoryDedupePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Unit-level coverage of the {@link KafkaLagProbe} fail-soft contract — the
 * AdminClient happens to be unreachable here (no broker) so describeTopics /
 * listOffsets / listConsumerGroupOffsets all time out, and the probe returns
 * sentinel values without throwing. The Micrometer fallback ({@code lagSeconds}
 * sourced from the registered Timer) is also exercised.
 */
class KafkaLagProbeTest {

    private MeterRegistry meterRegistry;
    private ProjectionMetrics projectionMetrics;
    private InMemoryDedupePort dedupe;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        projectionMetrics = new ProjectionMetrics(meterRegistry, java.time.Clock.systemUTC());
        dedupe = new InMemoryDedupePort();
    }

    @Test
    void probe_kafkaUnreachable_returnsSentinelOffsetLagWithoutThrowing() {
        KafkaLagProbe probe = newProbe(Duration.ofMillis(200));

        List<KafkaLagProbe.TopicLag> result = probe.probe();

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(tl -> {
            assertThat(tl.topic()).isNotBlank();
            assertThat(tl.consumerGroup()).isEqualTo("admin-projection");
            assertThat(tl.offsetLag()).isEqualTo(KafkaLagProbe.UNKNOWN_LAG);
            assertThat(tl.lastEventAt()).isNull();
        });
    }

    @Test
    void probe_micrometerFallbackPopulatesLagSeconds() {
        Timer.builder(ProjectionMetrics.LAG_METRIC)
                .tags(Tags.of("source_service", "inventory", "topic", "wms.inventory.adjusted.v1"))
                .register(meterRegistry)
                .record(Duration.ofMillis(3_500));

        KafkaLagProbe probe = newProbe(Duration.ofMillis(200));

        List<KafkaLagProbe.TopicLag> result = probe.probe();

        KafkaLagProbe.TopicLag adjusted = result.stream()
                .filter(t -> t.topic().equals("wms.inventory.adjusted.v1"))
                .findFirst().orElseThrow();
        assertThat(adjusted.lagSeconds()).isGreaterThan(3.0d);
    }

    @Test
    void probe_dedupeWatermarkPopulatesLastProjectedAt() {
        Instant ts = Instant.parse("2026-05-09T10:00:00Z");
        dedupe.recordWatermark("inventory.adjusted", ts);

        KafkaLagProbe probe = newProbe(Duration.ofMillis(200));

        List<KafkaLagProbe.TopicLag> result = probe.probe();

        KafkaLagProbe.TopicLag adjusted = result.stream()
                .filter(t -> t.topic().equals("wms.inventory.adjusted.v1"))
                .findFirst().orElseThrow();
        assertThat(adjusted.lastProjectedAt()).isEqualTo(ts);
    }

    private KafkaLagProbe newProbe(Duration timeout) {
        Map<String, Object> config = new HashMap<>();
        // Use an unreachable broker; AdminClient creation succeeds but every
        // call times out — exactly the fail-soft path under test.
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1");
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) timeout.toMillis());
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) timeout.toMillis());
        KafkaAdmin admin = new KafkaAdmin(config);

        return new KafkaLagProbe(admin, dedupe, TopicEventTypeMap.defaults(), meterRegistry,
                projectionMetrics, "admin-projection", timeout);
    }
}
