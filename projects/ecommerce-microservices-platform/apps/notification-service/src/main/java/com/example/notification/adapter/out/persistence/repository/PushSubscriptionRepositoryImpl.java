package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.mapper.PushSubscriptionPersistenceMapper;
import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.domain.model.PushSubscription;
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
        return jpaRepository.findByEndpoint(endpoint).map(mapper::toDomain);
    }

    @Override
    public void deleteByEndpoint(String endpoint) {
        jpaRepository.deleteByEndpoint(endpoint);
    }
}
