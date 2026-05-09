package com.wms.admin.infra.observability;

import com.wms.admin.application.port.AdminEventDedupePort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Snapshots {@code admin-projection} consumer-group state via {@link AdminClient}.
 *
 * <p>For each configured topic the probe returns:
 *
 * <ul>
 *   <li>{@code offsetLag} — sum across partitions of {@code (latestOffset -
 *       committedOffset)}. Sentinel {@code -1} on AdminClient timeout / error.</li>
 *   <li>{@code lastEventAt} — record timestamp of the latest produced record
 *       across partitions (AdminClient's {@code OffsetSpec.maxTimestamp()}).
 *       {@code null} when AdminClient cannot answer.</li>
 *   <li>{@code lastProjectedAt} — MAX({@code admin_event_dedupe.processed_at})
 *       across all eventTypes mapped to this topic (see
 *       {@link TopicEventTypeMap}).</li>
 *   <li>{@code lagSeconds} — derived from the {@code admin.projection.lag.seconds}
 *       Micrometer Timer max value when present, otherwise 0.</li>
 * </ul>
 *
 * <p>Fail-soft policy (per task edge case "#4 KafkaAdmin disconnect"): a probe
 * call must not throw — a topic's failure surfaces as sentinel values.
 * {@code OperationsController} returns 200 OK regardless.
 * {@link ProjectionMetrics#recordError(String)} with topic
 * {@code __probe__} records AdminClient-side errors for alerting.
 */
public class KafkaLagProbe {

    private static final Logger log = LoggerFactory.getLogger(KafkaLagProbe.class);

    /** AdminClient default; admin probe is read-only and idempotent on failure. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    /** Sentinel returned when AdminClient cannot compute the count. */
    public static final long UNKNOWN_LAG = -1L;
    private static final String PROBE_ERROR_TOPIC = "__probe__";

    private final KafkaAdmin kafkaAdmin;
    private final AdminEventDedupePort dedupePort;
    private final TopicEventTypeMap topicMap;
    private final MeterRegistry meterRegistry;
    private final ProjectionMetrics projectionMetrics;
    private final String consumerGroup;
    private final Duration timeout;

    public KafkaLagProbe(KafkaAdmin kafkaAdmin,
                         AdminEventDedupePort dedupePort,
                         TopicEventTypeMap topicMap,
                         MeterRegistry meterRegistry,
                         ProjectionMetrics projectionMetrics,
                         String consumerGroup) {
        this(kafkaAdmin, dedupePort, topicMap, meterRegistry, projectionMetrics, consumerGroup,
                DEFAULT_TIMEOUT);
    }

    public KafkaLagProbe(KafkaAdmin kafkaAdmin,
                         AdminEventDedupePort dedupePort,
                         TopicEventTypeMap topicMap,
                         MeterRegistry meterRegistry,
                         ProjectionMetrics projectionMetrics,
                         String consumerGroup,
                         Duration timeout) {
        this.kafkaAdmin = kafkaAdmin;
        this.dedupePort = dedupePort;
        this.topicMap = topicMap;
        this.meterRegistry = meterRegistry;
        this.projectionMetrics = projectionMetrics;
        this.consumerGroup = consumerGroup;
        this.timeout = timeout;
    }

    public List<TopicLag> probe() {
        List<String> topics = topicMap.topics();
        Map<String, Instant> lastProjectedByEventType = projectionWatermarks(topics);

        Map<String, List<TopicPartition>> partitionsByTopic;
        Map<TopicPartition, Long> committed;
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest;
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> maxTs;

        // Single AdminClient instance shared across describeTopics +
        // listConsumerGroupOffsets + listOffsets(latest) + listOffsets(maxTimestamp).
        // Each call retains its own try/catch fail-soft semantics — a transient
        // failure in one call falls back to its sentinel/empty value and does
        // NOT short-circuit the others. AdminClient.close() runs exactly once
        // when this try-with-resources block exits.
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            partitionsByTopic = describeTopics(admin, topics);
            committed = listCommittedOffsets(admin);
            List<TopicPartition> allPartitions = partitionsByTopic.values().stream()
                    .flatMap(List::stream).toList();
            latest = listOffsetsBySpec(admin, allPartitions, OffsetSpec.latest());
            maxTs = listOffsetsBySpec(admin, allPartitions, OffsetSpec.maxTimestamp());
        } catch (RuntimeException e) {
            // AdminClient.create() / close() failure — extremely rare (bad
            // config). Mirror the per-helper fallback so probe() never throws.
            recordProbeError("AdminClient lifecycle failed", e);
            partitionsByTopic = Map.of();
            committed = Map.of();
            latest = Map.of();
            maxTs = Map.of();
        }

        List<TopicLag> result = new ArrayList<>(topics.size());
        for (String topic : topics) {
            List<TopicPartition> tps = partitionsByTopic.getOrDefault(topic, List.of());
            long offsetLag = computeOffsetLag(tps, committed, latest);
            Instant lastEventAt = computeLastEventAt(tps, maxTs);
            Instant lastProjectedAt = computeLastProjectedAt(topic, lastProjectedByEventType);
            double lagSeconds = lookupLagSeconds(topic);
            result.add(new TopicLag(topic, consumerGroup, offsetLag, lastEventAt, lastProjectedAt,
                    lagSeconds));
        }
        return result;
    }

    private Map<String, List<TopicPartition>> describeTopics(AdminClient admin, List<String> topics) {
        if (topics.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, TopicDescription> described = admin.describeTopics(topics).allTopicNames()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<String, List<TopicPartition>> out = new HashMap<>();
            for (Map.Entry<String, TopicDescription> entry : described.entrySet()) {
                List<TopicPartition> tps = entry.getValue().partitions().stream()
                        .map(p -> new TopicPartition(entry.getKey(), p.partition()))
                        .collect(Collectors.toList());
                out.put(entry.getKey(), tps);
            }
            return out;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordProbeError("describeTopics interrupted", ie);
            return Map.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            recordProbeError("describeTopics failed", e);
            return Map.of();
        }
    }

    private Map<TopicPartition, Long> listCommittedOffsets(AdminClient admin) {
        try {
            ListConsumerGroupOffsetsResult result = admin.listConsumerGroupOffsets(consumerGroup);
            Map<TopicPartition, OffsetAndMetadata> map = result.partitionsToOffsetAndMetadata()
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Map<TopicPartition, Long> out = new HashMap<>();
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : map.entrySet()) {
                if (entry.getValue() != null) {
                    out.put(entry.getKey(), entry.getValue().offset());
                }
            }
            return out;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordProbeError("listConsumerGroupOffsets interrupted", ie);
            return Map.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            recordProbeError("listConsumerGroupOffsets failed", e);
            return Map.of();
        }
    }

    private Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> listOffsetsBySpec(
            AdminClient admin, List<TopicPartition> partitions, OffsetSpec spec) {
        if (partitions.isEmpty()) {
            return Map.of();
        }
        try {
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();
            for (TopicPartition tp : partitions) {
                request.put(tp, spec);
            }
            return admin.listOffsets(request).all().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            recordProbeError("listOffsets interrupted", ie);
            return Map.of();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            recordProbeError("listOffsets failed", e);
            return Map.of();
        }
    }

    private Map<String, Instant> projectionWatermarks(List<String> topics) {
        Set<String> all = topics.stream()
                .flatMap(t -> topicMap.eventTypesFor(t).stream())
                .collect(Collectors.toUnmodifiableSet());
        if (all.isEmpty()) {
            return Map.of();
        }
        try {
            return dedupePort.maxProcessedAtByEventType(all);
        } catch (RuntimeException e) {
            recordProbeError("dedupe watermark query failed", e);
            return Map.of();
        }
    }

    private long computeOffsetLag(List<TopicPartition> tps,
                                  Map<TopicPartition, Long> committed,
                                  Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest) {
        if (tps.isEmpty() || latest.isEmpty()) {
            return UNKNOWN_LAG;
        }
        long total = 0;
        boolean any = false;
        for (TopicPartition tp : tps) {
            ListOffsetsResult.ListOffsetsResultInfo info = latest.get(tp);
            if (info == null) {
                continue;
            }
            long latestOffset = info.offset();
            long committedOffset = committed.getOrDefault(tp, 0L);
            long delta = Math.max(latestOffset - committedOffset, 0);
            total += delta;
            any = true;
        }
        return any ? total : UNKNOWN_LAG;
    }

    private Instant computeLastEventAt(List<TopicPartition> tps,
                                       Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> maxTs) {
        long max = -1L;
        for (TopicPartition tp : tps) {
            ListOffsetsResult.ListOffsetsResultInfo info = maxTs.get(tp);
            if (info == null) continue;
            long ts = info.timestamp();
            if (ts > max) {
                max = ts;
            }
        }
        return max < 0 ? null : Instant.ofEpochMilli(max);
    }

    private Instant computeLastProjectedAt(String topic, Map<String, Instant> lastByEventType) {
        Instant max = null;
        for (String eventType : topicMap.eventTypesFor(topic)) {
            Instant ts = lastByEventType.get(eventType);
            if (ts == null) continue;
            if (max == null || ts.isAfter(max)) {
                max = ts;
            }
        }
        return max;
    }

    private double lookupLagSeconds(String topic) {
        Search search = meterRegistry.find(ProjectionMetrics.LAG_METRIC).tag("topic", topic);
        double max = 0.0d;
        for (Timer timer : search.timers()) {
            double v = timer.max(TimeUnit.SECONDS);
            if (Double.isFinite(v) && v > max) {
                max = v;
            }
        }
        return max;
    }

    private void recordProbeError(String message, Throwable t) {
        log.warn("KafkaLagProbe error: {} ({}: {})", message,
                t.getClass().getSimpleName(), t.getMessage());
        projectionMetrics.recordError(PROBE_ERROR_TOPIC);
    }

    /** Per-topic snapshot returned by {@link #probe()}. */
    public record TopicLag(String topic,
                           String consumerGroup,
                           long offsetLag,
                           Instant lastEventAt,
                           Instant lastProjectedAt,
                           double lagSeconds) {}
}
