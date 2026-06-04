package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventJpaRepository
        extends JpaRepository<ProcessedEventJpaEntity, String> {
}
