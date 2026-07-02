package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.PushSubscriptionJpaEntity;
import com.example.notification.domain.model.PushSubscription;
import org.springframework.stereotype.Component;

@Component
public class PushSubscriptionPersistenceMapper {

    public PushSubscription toDomain(PushSubscriptionJpaEntity entity) {
        return PushSubscription.reconstitute(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                entity.getEndpoint(),
                entity.getP256dh(),
                entity.getAuth(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public PushSubscriptionJpaEntity toEntity(PushSubscription subscription) {
        PushSubscriptionJpaEntity entity = new PushSubscriptionJpaEntity();
        entity.setId(subscription.getSubscriptionId());
        entity.setTenantId(subscription.getTenantId());
        entity.setUserId(subscription.getUserId());
        entity.setEndpoint(subscription.getEndpoint());
        entity.setP256dh(subscription.getP256dh());
        entity.setAuth(subscription.getAuth());
        entity.setCreatedAt(subscription.getCreatedAt());
        entity.setUpdatedAt(subscription.getUpdatedAt());
        return entity;
    }
}
