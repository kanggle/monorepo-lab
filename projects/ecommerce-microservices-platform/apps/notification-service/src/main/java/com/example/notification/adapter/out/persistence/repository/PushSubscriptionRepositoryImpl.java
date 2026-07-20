package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.mapper.PushSubscriptionPersistenceMapper;
import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.domain.model.PushSubscription;
import com.example.notification.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PushSubscriptionRepositoryImpl implements PushSubscriptionRepository {

    private final PushSubscriptionJpaRepository jpaRepository;
    private final PushSubscriptionPersistenceMapper mapper;

    @Override
    public PushSubscription save(PushSubscription subscription) {
        var saved = jpaRepository.save(mapper.toEntity(subscription));
        return mapper.toDomain(saved);
    }

    @Override
    public List<PushSubscription> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<PushSubscription> findByEndpoint(String endpoint) {
        return jpaRepository.findByTenantIdAndEndpoint(TenantContext.currentTenant(), endpoint)
                .map(mapper::toDomain);
    }

    @Override
    public void delete(PushSubscription subscription) {
        // Deletes by row identity, not by endpoint. The send-path prune runs on a Kafka
        // thread with no TenantContext, so a context-scoped delete would resolve to the
        // default tenant and remove the wrong row; the caller already holds the row it
        // means to remove (TASK-BE-540).
        jpaRepository.deleteById(subscription.getSubscriptionId());
    }
}
