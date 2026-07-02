package com.example.notification.application.port.out;

import com.example.notification.domain.model.PushSubscription;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for Web Push subscription persistence (TASK-BE-464).
 *
 * <p>{@link #findByUserId(String)} is the send-path lookup: it is called on the Kafka
 * thread (no HTTP {@code TenantContext}) with the globally-unique {@code userId} carried on
 * the event, so it returns exactly that user's subscriptions and cannot cross tenants.
 * Register/unregister run on the HTTP thread and are tenant-scoped via {@code TenantContext}.
 */
public interface PushSubscriptionRepository {

    PushSubscription save(PushSubscription subscription);

    List<PushSubscription> findByUserId(String userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
