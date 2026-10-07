package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.CallerEmployeePort;
import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.error.NotificationNotFoundException;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent mark-read (recipient-scoped, E6 fail-closed). The first call sets
 * {@code read = true} + {@code readAt = now}; a re-mark is a no-op that
 * preserves the original {@code readAt} (notification-api.md § mark-read). A
 * foreign-recipient / unknown id → {@link NotificationNotFoundException} (404). The recipient
 * is the employee linked to the caller's {@code sub} (§ v1.1, TASK-MONO-776); an unlinked
 * caller owns nothing → 404.
 */
@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository repository;
    private final ClockPort clock;
    private final NotificationMetricsPort metrics;
    private final CallerEmployeePort callerEmployee;

    @Transactional
    public Notification markRead(String tenantId, String callerSub, String id) {
        String recipient = RecipientScope.of(callerEmployee, callerSub, tenantId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        Notification notification = repository.findByIdForRecipient(tenantId, id, recipient)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        boolean wasUnread = !notification.read();
        notification.markRead(clock.now());
        if (wasUnread) {
            repository.save(notification);
        }
        metrics.markRead();
        return notification;
    }
}
