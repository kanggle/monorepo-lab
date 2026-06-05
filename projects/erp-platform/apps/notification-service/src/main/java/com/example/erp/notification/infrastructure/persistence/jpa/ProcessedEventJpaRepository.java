package com.example.erp.notification.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventJpaEntity, String> {
}
