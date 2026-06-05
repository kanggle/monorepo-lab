package com.example.erp.notification.domain.notification.repository;

import com.example.erp.notification.domain.notification.Notification;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the {@code notification} store. Every query carries the
 * recipient id (E6 data-scope, fail-closed — a caller may only see / mark their
 * own notifications) and the {@code tenantId} (defense-in-depth).
 */
public interface NotificationRepository {

    void save(Notification notification);

    /** Recipient-scoped lookup; a foreign-recipient id resolves to empty (→ 404). */
    Optional<Notification> findByIdForRecipient(String tenantId, String id, String recipientId);

    /** Newest-first inbox page for one recipient, optionally filtered by read flag. */
    List<Notification> findInbox(String tenantId, String recipientId, Boolean read,
                                 int page, int size);

    /** Total inbox count for one recipient (for {@code meta.totalElements}). */
    long countInbox(String tenantId, String recipientId, Boolean read);
}
