package com.wms.notification.adapter.outbound.persistence.jpa.dedupe;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventDedupeJpaRepository
        extends JpaRepository<NotificationEventDedupeJpaEntity, UUID> {
}
