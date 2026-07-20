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
 *
 * <p>{@link #findByEndpoint(String)} runs on the HTTP thread and IS tenant-scoped via
 * {@code TenantContext}. Until TASK-BE-540 this javadoc asserted that scoping while the
 * implementation queried globally — the sentence was the only place it was true.
 *
 * <p>{@link #delete(PushSubscription)} takes the row rather than an endpoint on purpose: the
 * send-path prune has no {@code TenantContext}, so an endpoint-keyed delete is either global
 * (deletes another tenant's row) or resolves to the default tenant (deletes the wrong one).
 */
public interface PushSubscriptionRepository {

    PushSubscription save(PushSubscription subscription);

    List<PushSubscription> findByUserId(String userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void delete(PushSubscription subscription);
}
