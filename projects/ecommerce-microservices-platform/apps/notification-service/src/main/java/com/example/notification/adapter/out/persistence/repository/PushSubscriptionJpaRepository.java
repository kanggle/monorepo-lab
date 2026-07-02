package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.PushSubscriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface PushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionJpaEntity, String> {

    /** Send-path lookup: all of a user's subscriptions (user_id is globally unique). */
    List<PushSubscriptionJpaEntity> findByUserId(String userId);

    /** Endpoint is a globally-unique push-service URL — at most one row. */
    Optional<PushSubscriptionJpaEntity> findByEndpoint(String endpoint);

    void deleteByEndpoint(String endpoint);
}
