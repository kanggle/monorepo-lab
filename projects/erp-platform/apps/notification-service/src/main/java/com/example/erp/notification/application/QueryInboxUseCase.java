package com.example.erp.notification.application;

import com.example.common.page.PageResult;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
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
    public PageResult<Notification> list(String tenantId, String recipientId, Boolean read,
                                         int page, int size) {
        var content = repository.findInbox(tenantId, recipientId, read, page, size);
        long total = repository.countInbox(tenantId, recipientId, read);
        metrics.inboxRead();
        // size is always >= 1 here (NotificationInboxController validates 1..MAX_SIZE before
        // calling this use case), so the ceiling-division is divide-by-zero-safe.
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Notification getOne(String tenantId, String recipientId, String id) {
        Notification notification = repository.findByIdForRecipient(tenantId, id, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        metrics.inboxRead();
        return notification;
    }
}
