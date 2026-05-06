package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventDedupeJpaRepository extends JpaRepository<EventDedupeJpaEntity, String> {
    boolean existsByEventId(String eventId);
}
