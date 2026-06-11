package com.example.fanplatform.notification.application;

import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists the caller's notifications (tenant + account scoped), newest first.
 * Cross-tenant / cross-account queries return an empty page — existence is not
 * leaked.
 */
@Service
@RequiredArgsConstructor
public class ListNotificationsUseCase {

    private final NotificationRepository repository;

    @Transactional(readOnly = true)
    public NotificationPage list(ActorContext actor, NotificationStatus status, int page, int size) {
        return repository.findInbox(actor.tenantId(), actor.accountId(), status, page, size);
    }
}
