package com.example.security.infrastructure.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Configuration
public class SecurityMetricsConfig {

    private final MeterRegistry meterRegistry;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final AdminClient adminClient;
    private final AtomicLong dlqDepthCache = new AtomicLong(0);
    /** Per-partition consumer lag cache keyed by (topic|group|partition) — read by Micrometer gauges. */
    private final Map<LagKey, LagHolder> lagGauges = new ConcurrentHashMap<>();
    private final String consumerGroup;
    /** Consumer group used by the DLQ drain (absent in this service — used only for depth math). */
    private final String dlqConsumerGroup;

    private static final List<String> DLQ_TOPICS = List.of(
            "auth.login.attempted.dlq",
            "auth.login.failed.dlq",
            "auth.login.succeeded.dlq",
            "auth.token.refreshed.dlq",
            "auth.token.reuse.detected.dlq",
            // TASK-BE-041b-fix Critical 2: register the account.locked DLQ so dlq_depth
            // alerts fire for poison-pill account.locked events (e.g., messages missing
            // eventId after the contract was tightened).
            "account.locked.dlq"
    );

    public SecurityMetricsConfig(MeterRegistry meterRegistry,
                                  KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry,
                                  KafkaProperties kafkaProperties,
                                  AdminClient adminClient) {
        this.meterRegistry = meterRegistry;
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.adminClient = adminClient;
        String groupId = kafkaProperties.getConsumer().getGroupId();
        this.consumerGroup = groupId != null ? groupId : "security-service";
        this.dlqConsumerGroup = this.consumerGroup + ".dlq";

        Gauge.builder("security_dlq_depth", this, config -> (double) config.dlqDepthCache.get())
                .description("Total DLQ depth across all DLQ topics (latestOffset - committedOffset per partition)")
                .register(meterRegistry);

        // NOTE: `security_consumer_lag` has been removed. Dashboards must aggregate the
        // per-partition `kafka_consumer_lag` gauge instead, e.g.
        //   sum(kafka_consumer_lag{service="security-service"})
        // See platform/observability.md for the migration note.
    }

    /**
     * Refreshes per-partition consumer lag gauges tagged (topic, group, partition)
     * by reading Kafka client metrics on each listener container.
     *
     * <p>Each cycle: collect the current set of observed keys, register new gauges,
     * update existing ones via {@link AtomicLong#set(long)}, and
     * <b>remove gauges for keys that disappeared</b> since the previous cycle so
     * that post-rebalance stale values do not linger (which would both mislead
     * dashboards and retain {@code AtomicLong} references).</p>
     */
    @Scheduled(fixedDelay = 10_000)
    void refreshConsumerLag() {
        try {
            Set<LagKey> observed = new HashSet<>();
            Collection<MessageListenerContainer> containers = kafkaListenerEndpointRegistry.getListenerContainers();
            for (MessageListenerContainer container : containers) {
                Map<String, Map<org.apache.kafka.common.MetricName, ? extends org.apache.kafka.common.Metric>> metrics = container.metrics();
                if (metrics == null) {
                    continue;
                }
                for (Map<org.apache.kafka.common.MetricName, ? extends org.apache.kafka.common.Metric> perClient : metrics.values()) {
                    for (Map.Entry<org.apache.kafka.common.MetricName, ? extends org.apache.kafka.common.Metric> e : perClient.entrySet()) {
                        org.apache.kafka.common.MetricName name = e.getKey();
                        if (!"records-lag".equals(name.name())) {
                            continue;
                        }
                        Map<String, String> tags = name.tags();
                        String topic = tags.get("topic");
                        String partition = tags.get("partition");
                        if (topic == null || partition == null) {
                            continue;
                        }
                        Object raw = e.getValue().metricValue();
                        if (!(raw instanceof Double d) || d.isNaN()) {
                            continue;
                        }
                        long value = d < 0 ? 0L : d.longValue();
                        LagKey key = new LagKey(topic, consumerGroup, partition);
                        observed.add(key);
                        LagHolder holder = lagGauges.computeIfAbsent(key, this::registerLagGauge);
                        holder.value.set(value);
                    }
                }
            }
            // Sweep: remove gauges whose (topic, group, partition) no longer appears in this cycle.
            // Prevents stale post-rebalance values and lets GC reclaim the AtomicLong references.
            Iterator<Map.Entry<LagKey, LagHolder>> it = lagGauges.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LagKey, LagHolder> entry = it.next();
                if (!observed.contains(entry.getKey())) {
                    try {
                        meterRegistry.remove(entry.getValue().meter);
                    } catch (Exception removeEx) {
                        log.debug("Failed to remove stale lag gauge for {}", entry.getKey(), removeEx);
                    }
                    it.remove();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to refresh kafka.consumer.lag gauges", e);
        }
    }

    private LagHolder registerLagGauge(LagKey k) {
        AtomicLong ref = new AtomicLong(0);
        Gauge gauge = Gauge.builder("kafka.consumer.lag", ref, AtomicLong::get)
                .description("Consumer lag (records) per partition for the configured group")
                .tags(Tags.of(
                        "topic", k.topic(),
                        "group", k.group(),
                        "partition", k.partition()))
                .register(meterRegistry);
        return new LagHolder(ref, gauge);
    }

    private record LagKey(String topic, String group, String partition) {}

    private record LagHolder(AtomicLong value, Meter meter) {}

    /**
     * Periodically computes DLQ depth and updates the cached value so that
     * Prometheus scrapes read from cache instead of blocking on Kafka.
     */
    @Scheduled(fixedDelay = 30_000)
    void refreshDlqDepth() {
        dlqDepthCache.set(computeDlqDepth());
    }

    /**
     * DLQ depth per partition = {@code latestOffset - committedOffset} for the
     * DLQ consumer group; the returned value is the sum across all DLQ partitions.
     *
     * <p>If the DLQ consumer group does not exist (no drain is running), all
     * records are treated as unprocessed — depth = sum of latest offsets. This
     * matches the operational intent of the metric: "how many poison-pill events
     * are awaiting human attention".</p>
     */
    long computeDlqDepth() {
        try {
            Set<String> existingTopics = adminClient.listTopics()
                    .names()
                    .get(5, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetSpec> offsetRequests = new HashMap<>();
            for (String dlqTopic : DLQ_TOPICS) {
                if (!existingTopics.contains(dlqTopic)) {
                    continue;
                }
                adminClient.describeTopics(List.of(dlqTopic))
                        .topicNameValues()
                        .get(dlqTopic)
                        .get(5, TimeUnit.SECONDS)
                        .partitions()
                        .forEach(partitionInfo ->
                                offsetRequests.put(
                                        new TopicPartition(dlqTopic, partitionInfo.partition()),
                                        OffsetSpec.latest()
                                )
                        );
            }

            if (offsetRequests.isEmpty()) {
                return 0;
            }

            ListOffsetsResult offsetsResult = adminClient.listOffsets(offsetRequests);
            Map<TopicPartition, OffsetAndMetadata> committed = fetchCommittedOffsetsForDlqGroup();

            long totalDepth = 0;
            for (TopicPartition tp : offsetRequests.keySet()) {
                try {
                    long latest = offsetsResult.partitionResult(tp).get(5, TimeUnit.SECONDS).offset();
                    long commit = 0L;
                    if (committed != null) {
                        OffsetAndMetadata meta = committed.get(tp);
                        commit = meta != null ? meta.offset() : 0L;
                    }
                    long delta = latest - commit;
                    if (delta < 0) {
                        delta = 0;
                    }
                    totalDepth += delta;
                } catch (Exception e) {
                    log.debug("Failed to get offset for partition {}", tp, e);
                }
            }
            return totalDepth;
        } catch (Exception e) {
            log.warn("Failed to compute DLQ depth metric, returning 0", e);
            return 0;
        }
    }

    /**
     * @return committed offsets for the DLQ consumer group, or {@code null} if the
     *         group does not exist (policy: callers treat null as "no commits",
     *         so depth collapses to {@code latest} per partition).
     */
    private Map<TopicPartition, OffsetAndMetadata> fetchCommittedOffsetsForDlqGroup() {
        try {
            ListConsumerGroupOffsetsResult result = adminClient.listConsumerGroupOffsets(dlqConsumerGroup);
            return result.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof GroupIdNotFoundException) {
                log.debug("DLQ consumer group {} absent — depth will equal latest offsets", dlqConsumerGroup);
                return null;
            }
            log.debug("Failed to fetch committed offsets for DLQ group {}", dlqConsumerGroup, e);
            return null;
        } catch (Exception e) {
            log.debug("Failed to fetch committed offsets for DLQ group {}", dlqConsumerGroup, e);
            return null;
        }
    }
}
