package com.example.membership.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionStatusHistoryJpaRepository
        extends JpaRepository<SubscriptionStatusHistoryJpaEntity, Long> {
}
