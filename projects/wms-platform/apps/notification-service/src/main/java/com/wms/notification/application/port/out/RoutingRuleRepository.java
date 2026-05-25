package com.wms.notification.application.port.out;

import com.wms.notification.domain.routing.RoutingRule;
import java.util.List;

/**
 * Read-port for routing rules. Cached behind a 60s TTL by the
 * implementation (architecture.md § Concurrency Control).
 */
public interface RoutingRuleRepository {

    /** All enabled rules matching the given event type — usually zero or one. */
    List<RoutingRule> findEnabledByEventType(String eventType);

    /** All rules — used by the admin surface in v2 and by tests today. */
    List<RoutingRule> findAll();
}
