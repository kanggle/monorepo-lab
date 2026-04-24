package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchJobExecutionPersistenceMapper 단위 테스트")
class BatchJobExecutionPersistenceMapperTest {

    private final BatchJobExecutionPersistenceMapper mapper = new BatchJobExecutionPersistenceMapper();

    @Test
    @DisplayName("도메인 → JpaEntity 변환 시 모든 필드가 매핑된다")
    void toEntity_mapsAllFields() {
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        BatchJobExecution execution = BatchJobExecution.reconstitute(
                1L, "cleanup-job", BatchJobStatus.COMPLETED,
                now, now.plusSeconds(120), null
        );

        BatchJobExecutionJpaEntity entity = mapper.toEntity(execution);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getJobName()).isEqualTo("cleanup-job");
        assertThat(entity.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(entity.getStartedAt()).isEqualTo(now);
        assertThat(entity.getFinishedAt()).isEqualTo(now.plusSeconds(120));
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("JpaEntity → 도메인 변환 시 모든 필드가 매핑된다")
    void toDomain_mapsAllFields() {
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        BatchJobExecution original = BatchJobExecution.reconstitute(
                2L, "aggregate-job", BatchJobStatus.FAILED,
                now, now.plusSeconds(60), "timeout"
        );
        BatchJobExecutionJpaEntity entity = mapper.toEntity(original);

        BatchJobExecution restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(2L);
        assertThat(restored.getJobName()).isEqualTo("aggregate-job");
        assertThat(restored.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(restored.getStartedAt()).isEqualTo(now);
        assertThat(restored.getFinishedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(restored.getErrorMessage()).isEqualTo("timeout");
    }

    @Test
    @DisplayName("RUNNING 상태의 도메인 → JpaEntity 변환 시 nullable 필드가 null로 매핑된다")
    void toEntity_runningStatus_mapsNullableFieldsAsNull() {
        BatchJobExecution execution = BatchJobExecution.start("running-job");

        BatchJobExecutionJpaEntity entity = mapper.toEntity(execution);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getJobName()).isEqualTo("running-job");
        assertThat(entity.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(entity.getStartedAt()).isNotNull();
        assertThat(entity.getFinishedAt()).isNull();
        assertThat(entity.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("RUNNING 상태의 JpaEntity → 도메인 변환 시 nullable 필드가 null로 매핑된다")
    void toDomain_runningStatus_mapsNullableFieldsAsNull() {
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        BatchJobExecutionJpaEntity entity = mapper.toEntity(
                BatchJobExecution.reconstitute(3L, "running-job", BatchJobStatus.RUNNING, now, null, null)
        );

        BatchJobExecution restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(3L);
        assertThat(restored.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(restored.getFinishedAt()).isNull();
        assertThat(restored.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("FAILED 상태의 왕복 변환 시 에러 메시지가 보존된다")
    void roundTrip_failedStatus_preservesErrorMessage() {
        BatchJobExecution original = BatchJobExecution.start("fail-job");
        original.fail("unexpected error");

        BatchJobExecutionJpaEntity entity = mapper.toEntity(original);
        BatchJobExecution restored = mapper.toDomain(entity);

        assertThat(restored.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(restored.getErrorMessage()).isEqualTo("unexpected error");
        assertThat(restored.getFinishedAt()).isEqualTo(original.getFinishedAt());
    }

    @Test
    @DisplayName("도메인 → JpaEntity → 도메인 왕복 변환 시 데이터 손실이 없다")
    void roundTrip_noDataLoss() {
        BatchJobExecution original = BatchJobExecution.start("test-job");
        original.complete();

        BatchJobExecutionJpaEntity entity = mapper.toEntity(original);
        BatchJobExecution restored = mapper.toDomain(entity);

        assertThat(restored.getId()).isEqualTo(original.getId());
        assertThat(restored.getJobName()).isEqualTo(original.getJobName());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getStartedAt()).isEqualTo(original.getStartedAt());
        assertThat(restored.getFinishedAt()).isEqualTo(original.getFinishedAt());
        assertThat(restored.getErrorMessage()).isEqualTo(original.getErrorMessage());
    }
}
