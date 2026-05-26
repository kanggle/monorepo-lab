package com.wms.admin.application.projection;

import com.wms.admin.api.dashboard.dto.ProjectionStatusResponse;
import com.wms.admin.application.repository.AdminEventDedupeRepository;
import com.wms.admin.infra.observability.KafkaLagProbe;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

/**
 * Application-layer service for the {@code admin-service-api.md § 6.2}
 * projection-status endpoint. Encapsulates dedupe counter aggregation +
 * lag-probe / listener-registry fallback so that
 * {@link com.wms.admin.api.dashboard.OperationsController} stays thin
 * (architecture.md § Layer Rules: controllers call application services).
 *
 * <p>Probe / registry are optional (standalone profile may have neither).
 * The service preserves the controller's prior behavior:
 *
 * <ul>
 *   <li>{@link KafkaLagProbe} present → per-topic entries with real
 *       {@code lagSeconds} + {@code lastEventAt} + {@code lastProjectedAt}.</li>
 *   <li>Probe absent, listener registry present → existence rows with
 *       {@code lagSeconds=0} and null timestamps.</li>
 *   <li>Both absent → no entries; lifetime dedupe counts still returned.</li>
 * </ul>
 */
@Service
public class ProjectionStatusService {

    private final AdminEventDedupeRepository dedupeRepository;
    private final KafkaListenerEndpointRegistry registry;
    private final KafkaLagProbe lagProbe;
    private final String consumerGroup;

    public ProjectionStatusService(AdminEventDedupeRepository dedupeRepository,
                                   ObjectProvider<KafkaListenerEndpointRegistry> registryProvider,
                                   ObjectProvider<KafkaLagProbe> probeProvider,
                                   @Value("${spring.kafka.consumer.group-id:admin-projection}")
                                           String consumerGroup) {
        this.dedupeRepository = dedupeRepository;
        this.registry = registryProvider.getIfAvailable();
        this.lagProbe = probeProvider.getIfAvailable();
        this.consumerGroup = consumerGroup;
    }

    public ProjectionStatusResponse computeStatus() {
        AdminEventDedupeRepository.LifetimeCounts counts = dedupeRepository.countLifetime();
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
