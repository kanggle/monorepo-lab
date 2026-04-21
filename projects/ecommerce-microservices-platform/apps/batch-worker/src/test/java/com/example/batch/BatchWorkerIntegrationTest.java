package com.example.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchWorker 통합 테스트")
class BatchWorkerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Flyway 마이그레이션으로 batch_job_execution_history 테이블이 생성된다")
    void flywayMigration_createsCustomTable() {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'batch_job_execution_history')",
                Boolean.class);

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Spring Batch 메타 테이블이 자동 생성된다")
    void springBatchMetaTables_areCreated() {
        List<String> expectedTables = List.of(
                "batch_job_instance",
                "batch_job_execution",
                "batch_job_execution_params",
                "batch_step_execution",
                "batch_step_execution_context",
                "batch_job_execution_context"
        );

        for (String table : expectedTables) {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ?)",
                    Boolean.class,
                    table);

            assertThat(exists)
                    .as("Spring Batch 메타 테이블 '%s'이 존재해야 한다", table)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("batch_job_execution_history 테이블의 컬럼 구조가 올바르다")
    void customTable_hasCorrectColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'batch_job_execution_history' ORDER BY ordinal_position",
                String.class);

        assertThat(columns).containsExactly("id", "job_name", "status", "started_at", "finished_at", "error_message");
    }
}
