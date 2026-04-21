package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
class BatchJobExecutionRepositoryImpl implements BatchJobExecutionRepository {

    private final BatchJobExecutionJpaRepository jpaRepository;
    private final BatchJobExecutionPersistenceMapper mapper;

    @Override
    public BatchJobExecution save(BatchJobExecution execution) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(execution)));
    }

    @Override
    public Optional<BatchJobExecution> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
}
