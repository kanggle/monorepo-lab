package com.example.erp.notification.application;

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
 * foreign-recipient / unknown id → {@link NotificationNotFoundException} (404).
 */
@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository repository;
    private final ClockPort clock;
    private final NotificationMetricsPort metrics;

    @Transactional
    public Notification markRead(String tenantId, String recipientId, String id) {
        Notification notification = repository.findByIdForRecipient(tenantId, id, recipientId)
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
