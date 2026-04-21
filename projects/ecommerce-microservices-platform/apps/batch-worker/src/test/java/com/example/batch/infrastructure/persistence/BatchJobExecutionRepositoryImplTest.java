package com.example.batch.infrastructure.persistence;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchJobExecutionRepositoryImpl 단위 테스트")
class BatchJobExecutionRepositoryImplTest {

    @InjectMocks
    private BatchJobExecutionRepositoryImpl repository;

    @Mock
    private BatchJobExecutionJpaRepository jpaRepository;

    @Mock
    private BatchJobExecutionPersistenceMapper mapper;

    @Test
    @DisplayName("save 호출 시 도메인 → 엔티티 변환 후 저장하고 다시 도메인으로 반환한다")
    void save_convertsAndDelegates() {
        BatchJobExecution execution = BatchJobExecution.start("cleanup-job");
        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                execution.getId(), execution.getJobName(), execution.getStatus(),
                execution.getStartedAt(), execution.getFinishedAt(), execution.getErrorMessage());
        BatchJobExecutionJpaEntity savedEntity = new BatchJobExecutionJpaEntity(
                1L, "cleanup-job", BatchJobStatus.RUNNING,
                execution.getStartedAt(), null, null);
        BatchJobExecution expectedResult = BatchJobExecution.reconstitute(
                1L, "cleanup-job", BatchJobStatus.RUNNING, execution.getStartedAt(), null, null);

        given(mapper.toEntity(execution)).willReturn(entity);
        given(jpaRepository.save(entity)).willReturn(savedEntity);
        given(mapper.toDomain(savedEntity)).willReturn(expectedResult);

        BatchJobExecution result = repository.save(execution);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getJobName()).isEqualTo("cleanup-job");
        assertThat(result.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        verify(mapper).toEntity(execution);
        verify(jpaRepository).save(entity);
        verify(mapper).toDomain(savedEntity);
    }

    @Test
    @DisplayName("findById 조회 성공 시 도메인 객체를 반환한다")
    void findById_found_returnsDomain() {
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                1L, "cleanup-job", BatchJobStatus.COMPLETED,
                now, now.plusSeconds(60), null);
        BatchJobExecution expected = BatchJobExecution.reconstitute(
                1L, "cleanup-job", BatchJobStatus.COMPLETED, now, now.plusSeconds(60), null);

        given(jpaRepository.findById(1L)).willReturn(Optional.of(entity));
        given(mapper.toDomain(entity)).willReturn(expected);

        Optional<BatchJobExecution> result = repository.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);
        assertThat(result.get().getJobName()).isEqualTo("cleanup-job");
        assertThat(result.get().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    @DisplayName("findById 조회 실패 시 빈 Optional을 반환한다")
    void findById_notFound_returnsEmpty() {
        given(jpaRepository.findById(999L)).willReturn(Optional.empty());

        Optional<BatchJobExecution> result = repository.findById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("FAILED 상태의 실행 이력을 저장하고 반환한다")
    void save_failedExecution_convertsCorrectly() {
        Instant now = Instant.parse("2025-06-01T12:00:00Z");
        BatchJobExecution execution = BatchJobExecution.reconstitute(
                null, "aggregate-job", BatchJobStatus.FAILED, now, now.plusSeconds(30), "DB connection lost");
        BatchJobExecutionJpaEntity entity = new BatchJobExecutionJpaEntity(
                execution.getId(), execution.getJobName(), execution.getStatus(),
                execution.getStartedAt(), execution.getFinishedAt(), execution.getErrorMessage());
        BatchJobExecutionJpaEntity savedEntity = new BatchJobExecutionJpaEntity(
                2L, "aggregate-job", BatchJobStatus.FAILED,
                now, now.plusSeconds(30), "DB connection lost");
        BatchJobExecution expectedResult = BatchJobExecution.reconstitute(
                2L, "aggregate-job", BatchJobStatus.FAILED, now, now.plusSeconds(30), "DB connection lost");

        given(mapper.toEntity(execution)).willReturn(entity);
        given(jpaRepository.save(entity)).willReturn(savedEntity);
        given(mapper.toDomain(savedEntity)).willReturn(expectedResult);

        BatchJobExecution result = repository.save(execution);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("DB connection lost");
    }
}
