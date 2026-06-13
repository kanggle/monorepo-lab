package com.example.settlement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSnapshotJpaRepository extends JpaRepository<OrderSnapshotJpaEntity, String> {
}
