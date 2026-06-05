package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.application.query.InboxPage;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only inbox queries (recipient-scoped, E6 fail-closed). Every query is
 * filtered to {@code recipient_id == caller.sub}; a foreign-recipient id is
 * indistinguishable from a non-existent one → {@link NotificationNotFoundException}
 * (404, no enumeration oracle).
 */
@Service
@RequiredArgsConstructor
public class QueryInboxUseCase {

    private final NotificationRepository repository;
    private final NotificationMetricsPort metrics;

    @Transactional(readOnly = true)
    public InboxPage list(String tenantId, String recipientId, Boolean read, int page, int size) {
        var content = repository.findInbox(tenantId, recipientId, read, page, size);
        long total = repository.countInbox(tenantId, recipientId, read);
        metrics.inboxRead();
        return new InboxPage(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public Notification getOne(String tenantId, String recipientId, String id) {
        Notification notification = repository.findByIdForRecipient(tenantId, id, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        metrics.inboxRead();
        return notification;
    }
}
