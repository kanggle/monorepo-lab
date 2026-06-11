package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationNotFoundException;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent mark-read (tenant + account scoped). The first call sets
 * {@code status = READ} + {@code readAt = now}; a re-mark of an already-READ
 * notification is a no-op that preserves the original {@code readAt}. A
 * cross-account / cross-tenant / unknown id → {@link NotificationNotFoundException}
 * (404, no existence leak).
 */
@Service
@RequiredArgsConstructor
public class MarkNotificationReadUseCase {

    private final NotificationRepository repository;
    private final ClockPort clock;

    @Transactional
    public Notification markRead(ActorContext actor, String id) {
        Notification notification = repository.findByIdScoped(id, actor.tenantId(), actor.accountId())
                .orElseThrow(() -> new NotificationNotFoundException(id));
        boolean wasUnread = !notification.isRead();
        notification.markRead(clock.now());
        if (wasUnread) {
            repository.save(notification);
        }
        return notification;
    }
}
