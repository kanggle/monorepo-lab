package com.wms.admin.api.dashboard;

import com.wms.admin.api.dashboard.dto.ProjectionStatusResponse;
import com.wms.admin.application.port.AdminEventDedupePort;
import com.wms.admin.infra.observability.KafkaLagProbe;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code admin-service-api.md § 6.2} — projection lag report.
 *
 * <p>When a {@link KafkaLagProbe} bean is present (default profile, Kafka
 * available) the response carries per-topic {@code offsetLag},
 * {@code lastEventAt} (AdminClient {@code OffsetSpec.maxTimestamp()}),
 * {@code lastProjectedAt} ({@code admin_event_dedupe.processed_at} MAX), and
 * {@code lagSeconds} (Micrometer Timer max for that topic). Under the
 * {@code standalone} profile the probe is absent and the listener registry is
 * the only fallback signal — the response degrades to existence rows.
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
@PreAuthorize("hasRole('WMS_ADMIN')")
public class OperationsController {

    private final AdminEventDedupePort dedupePort;
    private final KafkaListenerEndpointRegistry registry;
    private final KafkaLagProbe lagProbe;
    private final String consumerGroup;

    public OperationsController(AdminEventDedupePort dedupePort,
                                @Autowired(required = false) KafkaListenerEndpointRegistry registry,
                                @Autowired(required = false) KafkaLagProbe lagProbe,
                                @Value("${spring.kafka.consumer.group-id:admin-projection}")
                                        String consumerGroup) {
        this.dedupePort = dedupePort;
        this.registry = registry;
        this.lagProbe = lagProbe;
        this.consumerGroup = consumerGroup;
    }

    @GetMapping("/projection-status")
    public ProjectionStatusResponse projectionStatus() {
        AdminEventDedupePort.LifetimeCounts counts = dedupePort.countLifetime();
        List<ProjectionStatusResponse.ProjectionEntry> entries = new ArrayList<>();
        double worst = 0.0d;

        if (lagProbe != null) {
            for (KafkaLagProbe.TopicLag tl : lagProbe.probe()) {
                entries.add(new ProjectionStatusResponse.ProjectionEntry(
                        tl.topic(),
                        tl.consumerGroup(),
                        tl.lagSeconds(),
                        tl.lastEventAt(),
                        tl.lastProjectedAt()));
                if (tl.lagSeconds() > worst) {
                    worst = tl.lagSeconds();
                }
            }
        } else if (registry != null) {
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
                worst,
                counts.applied(),
                counts.ignoredDuplicate(),
                counts.ignoredDuplicateLate(),
                counts.failed());
    }
}
