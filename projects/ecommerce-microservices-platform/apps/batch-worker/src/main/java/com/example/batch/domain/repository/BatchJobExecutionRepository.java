package com.example.batch.domain.repository;

import com.example.batch.domain.model.BatchJobExecution;

import java.util.Optional;

public interface BatchJobExecutionRepository {

    BatchJobExecution save(BatchJobExecution execution);

    Optional<BatchJobExecution> findById(Long id);
}
