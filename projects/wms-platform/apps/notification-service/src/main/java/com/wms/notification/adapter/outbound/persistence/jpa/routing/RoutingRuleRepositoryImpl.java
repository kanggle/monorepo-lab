package com.wms.notification.adapter.outbound.persistence.jpa.routing;

import com.wms.notification.application.port.out.RoutingRuleRepository;
import com.wms.notification.domain.alert.AlertSeverity;
import com.wms.notification.domain.routing.RoutingRule;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps {@link RoutingRuleJpaEntity} ↔ {@link RoutingRule}. v1 has no caching
 * — direct DB hit on every consumer event. The TTL cache mentioned in
 * architecture.md § Concurrency Control is deferred to a follow-up task
 * once we have routing-edit volume to optimise (admin v2).
 */
@Component
public class RoutingRuleRepositoryImpl implements RoutingRuleRepository {

    private final RoutingRuleJpaRepository repository;
    private final RoutingMatcherJsonCodec codec;

    public RoutingRuleRepositoryImpl(RoutingRuleJpaRepository repository,
                                     RoutingMatcherJsonCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingRule> findEnabledByEventType(String eventType) {
        return repository.findByEventTypeAndEnabledTrue(eventType).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingRule> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private RoutingRule toDomain(RoutingRuleJpaEntity row) {
        return new RoutingRule(
                row.getId(),
                row.getEventType(),
                codec.decode(row.getMatcherJson()),
                codec.decodeChannelTargets(row.getChannelTargetsJson()),
                AlertSeverity.valueOf(row.getSeverity()),
                row.isEnabled(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
