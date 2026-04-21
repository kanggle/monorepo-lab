package com.example.batch.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface BatchJobExecutionJpaRepository extends JpaRepository<BatchJobExecutionJpaEntity, Long> {
}
