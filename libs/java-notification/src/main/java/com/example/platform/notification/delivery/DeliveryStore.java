package com.example.platform.notification.delivery;

import java.time.Instant;
import java.util.List;

/**
 * Persistence port for {@link DeliveryRecord}, owned by the lib and implemented by
 * each service's adapter (JPA / etc.). The lib defines the contract; the service
 * keeps its own table, idempotency-key uniqueness, and tenancy.
 *
 * <p>Lifted from the wms reference {@code DeliveryRepository}. The reference's
 * retry scheduler relies on {@link #findDuePending(Instant, int)} returning rows
 * under {@code SELECT … FOR UPDATE SKIP LOCKED} so two workers cannot double-fire
 * the same delivery. <b>That row-locking + the surrounding {@code @Scheduled} tick
 * stay service-side</b> (the lib does not bake in a scheduler or a DB dialect) —
 * the adapter implements the locked query; the dispatcher (per row) runs inside the
 * service's own {@code @Transactional(REQUIRES_NEW)} bean.
 */
public interface DeliveryStore {

    /**
     * Pick PENDING rows whose {@code scheduledRetryAt <= now}, newest-eligible first,
     * up to {@code limit}. The adapter is expected to issue this under
     * {@code FOR UPDATE SKIP LOCKED} (the lock releases on the caller's commit).
     */
    List<DeliveryRecord> findDuePending(Instant now, int limit);

    /**
     * Persist the record's current state (insert or update). The adapter applies
     * optimistic-lock conflict detection (the JPA base carries {@code @Version}).
     */
    void save(DeliveryRecord record);
}
