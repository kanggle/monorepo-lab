package com.example.batch.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BatchJobExecution 단위 테스트")
class BatchJobExecutionTest {

    @Test
    @DisplayName("start로 생성하면 RUNNING 상태이다")
    void start_createsRunningExecution() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        assertThat(execution.getJobName()).isEqualTo("test-job");
        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(execution.getStartedAt()).isNotNull();
        assertThat(execution.getFinishedAt()).isNull();
        assertThat(execution.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("complete 호출 시 COMPLETED 상태로 변경된다")
    void complete_changesStatusToCompleted() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        execution.complete();

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(execution.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("fail 호출 시 FAILED 상태와 에러 메시지가 설정된다")
    void fail_changesStatusToFailedWithMessage() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        execution.fail("Something went wrong");

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(execution.getFinishedAt()).isNotNull();
        assertThat(execution.getErrorMessage()).isEqualTo("Something went wrong");
    }

    @Test
    @DisplayName("reconstitute로 복원된 객체는 모든 필드를 유지한다")
    void reconstitute_restoresAllFields() {
        Instant now = Instant.now();
        BatchJobExecution execution = BatchJobExecution.reconstitute(
                1L, "test-job", BatchJobStatus.COMPLETED, now, now.plusSeconds(60), null
        );

        assertThat(execution.getId()).isEqualTo(1L);
        assertThat(execution.getJobName()).isEqualTo("test-job");
        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(execution.getStartedAt()).isEqualTo(now);
        assertThat(execution.getFinishedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(execution.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("start에 null jobName 전달 시 예외가 발생한다")
    void start_nullJobName_throws() {
        assertThatThrownBy(() -> BatchJobExecution.start(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobName");
    }

    @Test
    @DisplayName("start에 blank jobName 전달 시 예외가 발생한다")
    void start_blankJobName_throws() {
        assertThatThrownBy(() -> BatchJobExecution.start("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobName");
    }

    @Test
    @DisplayName("fail에 null errorMessage 전달 시 예외가 발생한다")
    void fail_nullErrorMessage_throws() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        assertThatThrownBy(() -> execution.fail(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }

    @Test
    @DisplayName("fail에 blank errorMessage 전달 시 예외가 발생한다")
    void fail_blankErrorMessage_throws() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        assertThatThrownBy(() -> execution.fail("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorMessage");
    }

    @Test
    @DisplayName("id, jobName, startedAt 필드는 final이다")
    void immutableFields_areFinal() {
        String[] expectedFinalFields = {"id", "jobName", "startedAt"};

        for (String fieldName : expectedFinalFields) {
            Field field = Arrays.stream(BatchJobExecution.class.getDeclaredFields())
                    .filter(f -> f.getName().equals(fieldName))
                    .findFirst()
                    .orElseThrow();
            assertThat(Modifier.isFinal(field.getModifiers()))
                    .as("필드 '%s'는 final이어야 한다", fieldName)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("도메인 모델에 JPA 의존성이 없다")
    void domainModel_hasNoJpaDependency() {
        Class<BatchJobExecution> clazz = BatchJobExecution.class;

        assertThat(clazz.getAnnotations())
                .noneMatch(a -> a.annotationType().getPackageName().startsWith("jakarta.persistence"));
    }

    @Test
    @DisplayName("complete 호출 시 finishedAt이 startedAt 이후이다")
    void complete_finishedAtIsAfterOrEqualToStartedAt() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        execution.complete();

        assertThat(execution.getFinishedAt()).isAfterOrEqualTo(execution.getStartedAt());
    }

    @Test
    @DisplayName("fail 호출 시 finishedAt이 startedAt 이후이다")
    void fail_finishedAtIsAfterOrEqualToStartedAt() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        execution.fail("error occurred");

        assertThat(execution.getFinishedAt()).isAfterOrEqualTo(execution.getStartedAt());
    }

    @Test
    @DisplayName("start로 생성된 실행의 id는 null이다")
    void start_idIsNull() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");

        assertThat(execution.getId()).isNull();
    }

    @Test
    @DisplayName("reconstitute로 FAILED 상태 복원 시 에러 메시지가 보존된다")
    void reconstitute_failedStatus_preservesErrorMessage() {
        Instant now = Instant.now();
        BatchJobExecution execution = BatchJobExecution.reconstitute(
                10L, "failing-job", BatchJobStatus.FAILED,
                now, now.plusSeconds(5), "DB connection timeout"
        );

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(execution.getErrorMessage()).isEqualTo("DB connection timeout");
        assertThat(execution.getFinishedAt()).isEqualTo(now.plusSeconds(5));
    }

    @Test
    @DisplayName("reconstitute로 RUNNING 상태 복원 시 finishedAt과 errorMessage는 null이다")
    void reconstitute_runningStatus_nullFinishedAtAndErrorMessage() {
        Instant now = Instant.now();
        BatchJobExecution execution = BatchJobExecution.reconstitute(
                20L, "running-job", BatchJobStatus.RUNNING,
                now, null, null
        );

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.RUNNING);
        assertThat(execution.getFinishedAt()).isNull();
        assertThat(execution.getErrorMessage()).isNull();
    }

    @Test
    @DisplayName("complete 후 다시 complete를 호출하면 finishedAt이 갱신된다")
    void complete_calledTwice_updatesFinishedAt() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");
        execution.complete();
        Instant firstFinishedAt = execution.getFinishedAt();

        execution.complete();

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(execution.getFinishedAt()).isAfterOrEqualTo(firstFinishedAt);
    }

    @Test
    @DisplayName("complete 후 fail을 호출하면 FAILED 상태로 전이된다")
    void complete_thenFail_changesStatusToFailed() {
        BatchJobExecution execution = BatchJobExecution.start("test-job");
        execution.complete();

        execution.fail("late error detected");

        assertThat(execution.getStatus()).isEqualTo(BatchJobStatus.FAILED);
        assertThat(execution.getErrorMessage()).isEqualTo("late error detected");
    }

    @Test
    @DisplayName("BatchJobStatus 열거형은 3개의 값을 가진다")
    void batchJobStatus_hasThreeValues() {
        assertThat(BatchJobStatus.values()).containsExactlyInAnyOrder(
                BatchJobStatus.RUNNING,
                BatchJobStatus.COMPLETED,
                BatchJobStatus.FAILED
        );
    }
}
