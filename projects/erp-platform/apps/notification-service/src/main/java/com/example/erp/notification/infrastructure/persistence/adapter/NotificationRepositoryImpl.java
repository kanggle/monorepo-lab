package com.example.erp.notification.infrastructure.persistence.adapter;

import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationJpaEntity;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    @Override
    public void save(Notification notification) {
        NotificationJpaEntity e = jpa.findById(notification.id())
                .orElseGet(NotificationJpaEntity::new);
        e.setId(notification.id());
        e.setTenantId(notification.tenantId());
        e.setRecipientId(notification.recipientId());
        e.setType(notification.type());
        e.setTitle(notification.title());
        e.setBody(notification.body());
        e.setSourceType(notification.source().sourceType());
        e.setSourceId(notification.source().sourceId());
        e.setRead(notification.read());
        e.setCreatedAt(notification.createdAt());
        e.setReadAt(notification.readAt().orElse(null));
        jpa.save(e);
    }

    @Override
    public Optional<Notification> findByIdForRecipient(String tenantId, String id, String recipientId) {
        return jpa.findByIdAndTenantIdAndRecipientId(id, tenantId, recipientId).map(this::toDomain);
    }

    @Override
    public List<Notification> findInbox(String tenantId, String recipientId, Boolean read,
                                        int page, int size) {
        return jpa.findInbox(tenantId, recipientId, read, PageRequest.of(page, size))
                .map(this::toDomain).getContent();
    }

    @Override
    public long countInbox(String tenantId, String recipientId, Boolean read) {
        return jpa.countInbox(tenantId, recipientId, read);
    }

    private Notification toDomain(NotificationJpaEntity e) {
        return new Notification(
                e.getId(), e.getTenantId(), e.getRecipientId(), e.getType(),
                e.getTitle(), e.getBody(),
                new SourceRef(e.getSourceType(), e.getSourceId()),
                e.isRead(), e.getCreatedAt(), e.getReadAt());
    }
}
