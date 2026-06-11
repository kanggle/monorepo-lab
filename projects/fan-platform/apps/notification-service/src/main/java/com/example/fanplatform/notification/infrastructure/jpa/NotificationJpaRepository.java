package com.example.fanplatform.notification.infrastructure.jpa;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Notification}. Every read method is
 * tenant + account scoped (multi-tenant.md M2).
 */
public interface NotificationJpaRepository extends JpaRepository<Notification, String> {

    boolean existsBySourceEventId(String sourceEventId);

    Optional<Notification> findByIdAndTenantIdAndAccountId(String id, String tenantId, String accountId);

    Page<Notification> findByTenantIdAndAccountId(String tenantId, String accountId, Pageable pageable);

    Page<Notification> findByTenantIdAndAccountIdAndStatus(
            String tenantId, String accountId, NotificationStatus status, Pageable pageable);
}
