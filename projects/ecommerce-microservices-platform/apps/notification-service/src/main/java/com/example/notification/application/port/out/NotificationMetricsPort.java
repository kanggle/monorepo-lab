package com.example.notification.application.port.out;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationFailureReason;

/**
 * Outbound port for delivery-outcome metrics (TASK-BE-533).
 *
 * <p>ADR-006 gates its own ACCEPT on notification-service counting send failures rather than only
 * logging them. The two counters behind this port are the ones
 * {@code specs/services/notification-service/observability.md} declares and that
 * {@code infra/prometheus/alert-rules.yml}'s {@code notification_delivery} group alerts on:
 *
 * <ul>
 *   <li>{@code notification_sent_total{channel}}</li>
 *   <li>{@code notification_failed_total{channel, reason}}</li>
 * </ul>
 *
 * <p><strong>One population.</strong> Both counters are incremented exactly once per persisted
 * notification row — the same unit that gets {@code markSent()} / {@code markFailed()} — so the
 * alert's {@code failed / (failed + sent)} ratio is a true rate. Senders must therefore not
 * increment these counters per delivery attempt; a sender that fans out (Web Push, one attempt per
 * subscription) reports its aggregate outcome by returning normally or throwing.
 */
public interface NotificationMetricsPort {

    /** A notification was handed to its channel successfully. */
    void recordSent(NotificationChannel channel);

    /** A notification failed to send, classified by a bounded reason. */
    void recordFailed(NotificationChannel channel, NotificationFailureReason reason);
}
