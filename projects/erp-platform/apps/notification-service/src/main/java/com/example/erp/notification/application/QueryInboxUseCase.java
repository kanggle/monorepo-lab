package com.example.erp.notification.application;

import com.example.common.page.PageResult;
import com.example.erp.notification.application.port.outbound.CallerEmployeePort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read-only inbox queries (recipient-scoped, E6 fail-closed). Every query is filtered to
 * {@code recipient_id == the employee linked to caller.sub} (notification-api.md § v1.1,
 * TASK-MONO-776 — it used to be {@code == caller.sub}, which compared an account UUID with an
 * employee id). An unlinked caller has an empty inbox and every detail is 404; a
 * foreign-recipient id is indistinguishable from a non-existent one →
 * {@link NotificationNotFoundException} (404, no enumeration oracle).
 */
@Service
@RequiredArgsConstructor
public class QueryInboxUseCase {

    private final NotificationRepository repository;
    private final NotificationMetricsPort metrics;
    private final CallerEmployeePort callerEmployee;

    @Transactional(readOnly = true)
    public PageResult<Notification> list(String tenantId, String callerSub, Boolean read,
                                         int page, int size) {
        Optional<String> recipient = RecipientScope.of(callerEmployee, callerSub, tenantId);
        metrics.inboxRead();
        if (recipient.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        var content = repository.findInbox(tenantId, recipient.get(), read, page, size);
        long total = repository.countInbox(tenantId, recipient.get(), read);
        // size is always >= 1 here (NotificationInboxController validates 1..MAX_SIZE before
        // calling this use case), so the ceiling-division is divide-by-zero-safe.
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResult<>(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public Notification getOne(String tenantId, String callerSub, String id) {
        String recipient = RecipientScope.of(callerEmployee, callerSub, tenantId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        Notification notification = repository.findByIdForRecipient(tenantId, id, recipient)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        metrics.inboxRead();
        return notification;
    }
}
