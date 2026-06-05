package com.example.security.infrastructure.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that SecurityMetricsConfig registers per-partition
 * {@code kafka.consumer.lag} gauges tagged with topic/group/partition
 * and that stale gauges are swept after a rebalance.
 */
class SecurityMetricsConfigTest {

    private static MetricName lagMetricFor(String topic, String partition) {
        return new MetricName(
                "records-lag",
                "consumer-fetch-manager-metrics",
                "lag",
                Map.of(
                        "client-id", "consumer-security-service-1",
                        "topic", topic,
                        "partition", partition
                )
        );
    }

    private static Metric doubleMetric(double value) {
        Metric m = mock(Metric.class);
        when(m.metricValue()).thenReturn(value);
        return m;
    }

    private static SecurityMetricsConfig newConfig(SimpleMeterRegistry registry,
                                                    KafkaListenerEndpointRegistry listenerRegistry) {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.getConsumer().setGroupId("security-service");
        AdminClient adminClient = mock(AdminClient.class);
        return new SecurityMetricsConfig(registry, listenerRegistry, kafkaProperties, adminClient);
    }

    @Test
    void refreshConsumerLag_registersPerPartitionGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaListenerEndpointRegistry listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        Metric lag0 = doubleMetric(42.0);
        Map<String, Map<MetricName, Metric>> metricsMap = Map.of(
                "consumer-security-service-1",
                Map.of(lagMetricFor("auth.login.succeeded", "0"), lag0)
        );
        when(container.metrics()).thenReturn((Map) metricsMap);
        when(listenerRegistry.getListenerContainers()).thenReturn(List.<MessageListenerContainer>of(container));

        SecurityMetricsConfig config = newConfig(registry, listenerRegistry);
        config.refreshConsumerLag();

        Meter meter = registry.find("kafka.consumer.lag")
                .tag("topic", "auth.login.succeeded")
                .tag("group", "security-service")
                .tag("partition", "0")
                .meter();
        assertThat(meter).as("kafka.consumer.lag gauge registered").isNotNull();
        double value = registry.find("kafka.consumer.lag")
                .tag("partition", "0")
                .gauge()
                .value();
        assertThat(value).isEqualTo(42.0d);
    }

    @Test
    void refreshConsumerLag_sweepsStaleGaugesAfterRebalance() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaListenerEndpointRegistry listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);
        when(listenerRegistry.getListenerContainers()).thenReturn(List.<MessageListenerContainer>of(container));

        SecurityMetricsConfig config = newConfig(registry, listenerRegistry);

        // Cycle 1: consumer owns partitions 0 and 1 of auth.login.succeeded.
        Metric lag0v1 = doubleMetric(10.0);
        Metric lag1v1 = doubleMetric(20.0);
        Map<String, Map<MetricName, Metric>> cycle1 = Map.of(
                "consumer-security-service-1",
                Map.of(
                        lagMetricFor("auth.login.succeeded", "0"), lag0v1,
                        lagMetricFor("auth.login.succeeded", "1"), lag1v1
                )
        );
        when(container.metrics()).thenReturn((Map) cycle1);
        config.refreshConsumerLag();

        assertThat(registry.find("kafka.consumer.lag").tag("partition", "0").gauge()).isNotNull();
        assertThat(registry.find("kafka.consumer.lag").tag("partition", "1").gauge()).isNotNull();

        // Cycle 2: rebalance — partition 1 moved to a peer, only partition 0 remains observed.
        Metric lag0v2 = doubleMetric(5.0);
        Map<String, Map<MetricName, Metric>> cycle2 = Map.of(
                "consumer-security-service-1",
                Map.of(lagMetricFor("auth.login.succeeded", "0"), lag0v2)
        );
        when(container.metrics()).thenReturn((Map) cycle2);
        config.refreshConsumerLag();

        assertThat(registry.find("kafka.consumer.lag").tag("partition", "0").gauge().value())
                .isEqualTo(5.0d);
        assertThat(registry.find("kafka.consumer.lag").tag("partition", "1").gauge())
                .as("stale gauge for partition 1 must be removed after rebalance")
                .isNull();
    }

    @Test
    void legacySecurityConsumerLagGaugeIsNotRegistered() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KafkaListenerEndpointRegistry listenerRegistry = mock(KafkaListenerEndpointRegistry.class);
        when(listenerRegistry.getListenerContainers()).thenReturn(List.<MessageListenerContainer>of());

        newConfig(registry, listenerRegistry);

        assertThat(registry.find("security_consumer_lag").gauge())
                .as("deprecated aggregate lag gauge must not be registered")
                .isNull();
    }
}
