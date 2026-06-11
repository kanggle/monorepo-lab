package com.example.fanplatform.notification.domain.notification;

import java.util.Optional;

/**
 * Domain port for {@link Notification} persistence. Every read is tenant +
 * account scoped (multi-tenant.md M2); a cross-tenant / cross-account read
 * returns {@link Optional#empty()} / an empty page so the service never leaks
 * another account's or tenant's notifications.
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** True if a notification with this {@code sourceEventId} already exists. */
    boolean existsBySourceEventId(String sourceEventId);

    /** A single notification scoped to the caller (tenant + account). */
    Optional<Notification> findByIdScoped(String id, String tenantId, String accountId);

    /**
     * The caller's notifications, newest first, paginated.
     *
     * @param status optional status filter; {@code null} returns all states.
     */
    NotificationPage findInbox(String tenantId, String accountId,
                               NotificationStatus status, int page, int size);
}
