package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobExecution;
import org.springframework.stereotype.Component;

@Component
class BatchJobExecutionPersistenceMapper {

    BatchJobExecution toDomain(BatchJobExecutionJpaEntity entity) {
        return BatchJobExecution.reconstitute(
                entity.getId(),
                entity.getJobName(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getErrorMessage()
        );
    }

    BatchJobExecutionJpaEntity toEntity(BatchJobExecution execution) {
        return new BatchJobExecutionJpaEntity(
                execution.getId(),
                execution.getJobName(),
                execution.getStatus(),
                execution.getStartedAt(),
                execution.getFinishedAt(),
                execution.getErrorMessage()
        );
    }
}
