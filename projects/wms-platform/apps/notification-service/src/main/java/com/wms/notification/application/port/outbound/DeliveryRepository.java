package com.wms.notification.application.port.outbound;

import com.wms.notification.domain.delivery.NotificationDelivery;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link NotificationDelivery}.
 *
 * <p>Implementations participate in the caller's transaction. The retry
 * scheduler additionally relies on
 * {@link #findAndLockPendingDueForRetry(Instant, int)} returning rows under
 * {@code SELECT … FOR UPDATE SKIP LOCKED} so two worker instances cannot
 * double-fire the same delivery (architecture.md § Concurrency Control).
 */
public interface DeliveryRepository {

    /** Insert a new delivery row. Throws on idempotency-key UNIQUE collision. */
    void save(NotificationDelivery delivery);

    /** Persist updates to an existing delivery — JPA optimistic-lock aware. */
    void update(NotificationDelivery delivery);

    Optional<NotificationDelivery> findById(UUID id);

    Optional<NotificationDelivery> findByIdempotencyKey(String idempotencyKey);

    /** Read-only — used by tests + admin v2 dashboards. */
    List<NotificationDelivery> findByEventId(UUID eventId);

    /**
     * Pick PENDING rows whose {@code scheduledRetryAt &lt;= now} under
     * {@code FOR UPDATE SKIP LOCKED}. The lock is released when the
     * surrounding transaction commits.
     */
    List<NotificationDelivery> findAndLockPendingDueForRetry(Instant now, int batchSize);
}
