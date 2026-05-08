package com.wms.notification.adapter.outbound.persistence.jpa.routing;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingRuleJpaRepository extends JpaRepository<RoutingRuleJpaEntity, UUID> {

    List<RoutingRuleJpaEntity> findByEventTypeAndEnabledTrue(String eventType);
}
