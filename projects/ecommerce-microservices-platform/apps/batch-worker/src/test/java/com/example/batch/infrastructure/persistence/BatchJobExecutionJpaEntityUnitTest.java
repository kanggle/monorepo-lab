package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchJobExecutionJpaEntity 단위 테스트")
class BatchJobExecutionJpaEntityUnitTest {

    @Test
    @DisplayName("모든 필드를 전달하여 생성하면 각 필드가 올바르게 설정된다")
    void constructor_allFields_setsAllFieldsCorrectly() {
        Instant startedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant finishedAt = Instant.parse("2025-06-01T10:05:00Z");

        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                1L, "cleanup-job", BatchJobStatus.COMPLETED,
                startedAt, finishedAt, null
        );

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getJobName()).isEqualTo("cleanup-job");
        assertThat(entity.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(entity.getStartedAt()).isEqualTo(startedAt);
        assertThat(entity.getFinishedAt()).isEqualTo(finishedAt);
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("FAILED 상태로 생성하면 에러 메시지가 보존된다")
    void constructor_failedStatus_preservesErrorMessage() {
        Instant startedAt = Instant.parse("2025-06-01T10:00:00Z");
        Instant finishedAt = Instant.parse("2025-06-01T10:01:00Z");

        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                2L, "aggregate-job", BatchJobStatus.FAILED,
                startedAt, finishedAt, "DB connection timeout"
        );

        assertThat(entity.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(entity.getErrorMessage()).isEqualTo("DB connection timeout");
    }

    @Test
    @DisplayName("RUNNING 상태로 생성하면 finishedAt과 errorMessage가 null이다")
    void constructor_runningStatus_nullableFieldsAreNull() {
        Instant startedAt = Instant.parse("2025-06-01T10:00:00Z");

        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                null, "running-job", BatchJobStatus.RUNNING,
                startedAt, null, null
        );

        assertThat(entity.getId()).isNull();
        assertThat(entity.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(entity.getFinishedAt()).isNull();
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("id가 null인 경우 새로운 엔티티로 간주된다")
    void constructor_nullId_representsNewEntity() {
        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                null, "new-job", BatchJobStatus.RUNNING,
                Instant.now(), null, null
        );

        assertThat(entity.getId()).isNull();
    }
}
