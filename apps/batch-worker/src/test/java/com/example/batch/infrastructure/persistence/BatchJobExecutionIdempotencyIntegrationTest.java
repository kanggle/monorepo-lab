package com.example.batch.infrastructure.persistence;

import com.example.batch.AbstractIntegrationTest;
import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchJobExecution 멱등성 통합 테스트")
class BatchJobExecutionIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BatchJobExecutionRepository repository;

    @Test
    @DisplayName("동일 실행 이력을 두 번 저장해도 결과가 동일하다")
    void save_sameExecutionTwice_producesConsistentResult() {
        String jobName = "idempotent-job-" + UUID.randomUUID();
        BatchJobExecution execution = BatchJobExecution.start(jobName);
        execution.complete();

        BatchJobExecution firstSave = repository.save(execution);

        BatchJobExecution reconstituted = BatchJobExecution.reconstitute(
                firstSave.getId(), firstSave.getJobName(), firstSave.getStatus(),
                firstSave.getStartedAt(), firstSave.getFinishedAt(), firstSave.getErrorMessage()
        );
        BatchJobExecution secondSave = repository.save(reconstituted);

        assertThat(secondSave.getId()).isEqualTo(firstSave.getId());
        assertThat(secondSave.getJobName()).isEqualTo(firstSave.getJobName());
        assertThat(secondSave.getStatus()).isEqualTo(firstSave.getStatus());
        assertThat(secondSave.getStartedAt()).isEqualTo(firstSave.getStartedAt());
        assertThat(secondSave.getFinishedAt()).isEqualTo(firstSave.getFinishedAt());
        assertThat(secondSave.getErrorMessage()).isEqualTo(firstSave.getErrorMessage());
    }

    @Test
    @DisplayName("동일 ID로 complete를 두 번 저장해도 COMPLETED 상태가 유지된다")
    void save_completeCalledTwice_statusRemainsCompleted() {
        String jobName = "double-complete-job-" + UUID.randomUUID();
        BatchJobExecution execution = BatchJobExecution.start(jobName);
        execution.complete();
        BatchJobExecution saved = repository.save(execution);

        BatchJobExecution reconstituted = BatchJobExecution.reconstitute(
                saved.getId(), saved.getJobName(), saved.getStatus(),
                saved.getStartedAt(), saved.getFinishedAt(), saved.getErrorMessage()
        );
        reconstituted.complete();
        repository.save(reconstituted);

        Optional<BatchJobExecution> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(found.get().getJobName()).isEqualTo(jobName);
    }

    @Test
    @DisplayName("동일 ID로 fail을 두 번 저장해도 최종 에러 메시지가 보존된다")
    void save_failCalledTwice_lastErrorMessagePersisted() {
        String jobName = "double-fail-job-" + UUID.randomUUID();
        BatchJobExecution execution = BatchJobExecution.start(jobName);
        execution.fail("first error");
        BatchJobExecution saved = repository.save(execution);

        BatchJobExecution reconstituted = BatchJobExecution.reconstitute(
                saved.getId(), saved.getJobName(), saved.getStatus(),
                saved.getStartedAt(), saved.getFinishedAt(), saved.getErrorMessage()
        );
        reconstituted.fail("second error");
        repository.save(reconstituted);

        Optional<BatchJobExecution> found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(found.get().getErrorMessage()).isEqualTo("second error");
    }

    @Test
    @DisplayName("조회를 여러 번 반복해도 동일한 결과를 반환한다")
    void findById_calledMultipleTimes_returnsSameResult() {
        String jobName = "read-idempotent-job-" + UUID.randomUUID();
        BatchJobExecution execution = BatchJobExecution.start(jobName);
        execution.complete();
        BatchJobExecution saved = repository.save(execution);

        Optional<BatchJobExecution> first = repository.findById(saved.getId());
        Optional<BatchJobExecution> second = repository.findById(saved.getId());
        Optional<BatchJobExecution> third = repository.findById(saved.getId());

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(third).isPresent();

        assertThat(first.get().getId()).isEqualTo(second.get().getId()).isEqualTo(third.get().getId());
        assertThat(first.get().getJobName()).isEqualTo(second.get().getJobName()).isEqualTo(third.get().getJobName());
        assertThat(first.get().getStatus()).isEqualTo(second.get().getStatus()).isEqualTo(third.get().getStatus());
    }
}
