package com.wms.notification.application.service.fakes;

import com.wms.notification.application.port.outbound.RoutingRuleRepository;
import com.wms.notification.domain.routing.RoutingRule;
import java.util.ArrayList;
import java.util.List;

public class InMemoryRoutingRuleRepository implements RoutingRuleRepository {

    private final List<RoutingRule> rules = new ArrayList<>();

    public void add(RoutingRule rule) {
        rules.add(rule);
    }

    @Override
    public List<RoutingRule> findEnabledByEventType(String eventType) {
        return rules.stream()
                .filter(r -> r.enabled() && r.eventType().equals(eventType))
                .toList();
    }

    @Override
    public List<RoutingRule> findAll() {
        return List.copyOf(rules);
    }
}
