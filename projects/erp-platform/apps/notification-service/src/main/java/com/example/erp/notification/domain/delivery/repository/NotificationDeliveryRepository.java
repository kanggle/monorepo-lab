package com.example.erp.notification.domain.delivery.repository;

import com.example.erp.notification.domain.delivery.NotificationDelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Outbound port for the {@code notification_delivery} store. */
public interface NotificationDeliveryRepository {

    void save(NotificationDelivery delivery);

    /** Load one delivery by id (used by the retry scheduler — TASK-ERP-BE-020). */
    Optional<NotificationDelivery> findById(String id);

    /**
     * Ids of deliveries due for an external-channel attempt: {@code status = PENDING}
     * and {@code scheduled_retry_at <= now}, oldest-due first, at most {@code limit}
     * (TASK-ERP-BE-020). IN_APP deliveries are created already-DELIVERED, so any PENDING
     * row with a due {@code scheduled_retry_at} is necessarily an external delivery.
     */
    List<String> findDueDeliveryIds(Instant now, int limit);
}
