package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ProjectionStatusResponse;
import com.wms.admin.application.port.AdminEventDedupePort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 6.2} — projection lag report.
 *
 * <p>v1 v1: minimal aggregate — lifetime applied/duplicate counts come from
 * {@code admin_event_dedupe}; per-topic lag reflects whatever the
 * {@code KafkaListenerEndpointRegistry} reports (or empty if the
 * {@code standalone} profile disabled consumers). The runbook references this
 * endpoint as a catch-up verification input.
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasRole('WMS_ADMIN')")
public class OperationsController {

    private final AdminEventDedupePort dedupePort;
    private final KafkaListenerEndpointRegistry registry;
    private final String consumerGroup;

    public OperationsController(AdminEventDedupePort dedupePort,
                                @org.springframework.beans.factory.annotation.Autowired(
                                        required = false) KafkaListenerEndpointRegistry registry,
                                @Value("${spring.kafka.consumer.group-id:admin-projection}")
                                        String consumerGroup) {
        this.dedupePort = dedupePort;
        this.registry = registry;
        this.consumerGroup = consumerGroup;
    }

    @GetMapping("/projection-status")
    public ProjectionStatusResponse projectionStatus() {
        AdminEventDedupePort.LifetimeCounts counts = dedupePort.countLifetime();
        List<ProjectionStatusResponse.ProjectionEntry> entries = new ArrayList<>();
        // Per-topic lag reporting requires Kafka-side admin client introspection
        // (consumer-group offsets vs end offsets); v1 surfaces an empty list
        // when the registry is absent (standalone profile) and an entry per
        // listener container otherwise. The registry returns container ids
        // rather than per-topic lag, so v1 limits the response to existence
        // signals — exact lag SLI is from `admin.projection.lag.seconds` in
        // Prometheus.
        if (registry != null) {
            for (var container : registry.getListenerContainers()) {
                String[] topics = container.getContainerProperties().getTopics();
                if (topics == null) continue;
                for (String topic : topics) {
                    entries.add(new ProjectionStatusResponse.ProjectionEntry(
                            topic,
                            consumerGroup,
                            0.0d,
                            null,
                            null));
                }
            }
        }
        return new ProjectionStatusResponse(
                entries,
                0.0d,
                counts.applied(),
                counts.ignoredDuplicate(),
                counts.ignoredDuplicateLate(),
                counts.failed());
    }
}
