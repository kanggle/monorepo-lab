package com.example.fanplatform.notification.infrastructure.jpa;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationPage;
import com.example.fanplatform.notification.domain.notification.NotificationRepository;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * JPA adapter satisfying the {@link NotificationRepository} domain port. The
 * optional status filter chooses between the two derived queries; both are
 * tenant + account scoped and sorted newest-first.
 */
@Component
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    @Override
    public Notification save(Notification notification) {
        return jpa.save(notification);
    }

    @Override
    public boolean existsBySourceEventId(String sourceEventId) {
        return jpa.existsBySourceEventId(sourceEventId);
    }

    @Override
    public Optional<Notification> findByIdScoped(String id, String tenantId, String accountId) {
        return jpa.findByIdAndTenantIdAndAccountId(id, tenantId, accountId);
    }

    @Override
    public NotificationPage findInbox(String tenantId, String accountId,
                                      NotificationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Notification> result = (status == null)
                ? jpa.findByTenantIdAndAccountId(tenantId, accountId, pageable)
                : jpa.findByTenantIdAndAccountIdAndStatus(tenantId, accountId, status, pageable);
        return new NotificationPage(result.getContent(), page, size, result.getTotalElements());
    }
}
