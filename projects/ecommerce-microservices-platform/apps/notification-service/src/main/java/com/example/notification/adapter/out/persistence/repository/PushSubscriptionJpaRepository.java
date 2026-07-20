package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.PushSubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface PushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionJpaEntity, String> {

    /** Send-path lookup: all of a user's subscriptions (user_id is globally unique). */
    List<PushSubscriptionJpaEntity> findByUserId(String userId);

    /**
     * Endpoint lookup, scoped to a tenant (TASK-BE-540). An endpoint is issued per
     * (browser, origin, VAPID key); this deployment has one origin and one VAPID keypair,
     * so the SAME browser signing in as a user of another tenant yields the SAME endpoint
     * string. A global lookup therefore returned another tenant's row and the upsert
     * rotated that tenant's keys — while {@code uq_push_subscriptions_tenant_endpoint}
     * (which is per-tenant, and correct) was never reached because the INSERT branch was
     * never taken. The previous comment here claimed the endpoint was globally unique with
     * at most one row; the constraint declared the opposite, and the constraint was right.
     */
    Optional<PushSubscriptionJpaEntity> findByTenantIdAndEndpoint(String tenantId, String endpoint);
}
