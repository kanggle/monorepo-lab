package com.example.finance.account.presentation.advice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the selective {@link GlobalExceptionHandler#handleIntegrity} is actually REACHED
 * through Spring's real {@code ExceptionHandlerExceptionResolver} (TASK-MONO-450), not merely
 * that it computes the right value when invoked directly. A handler that was never registered —
 * or that lost the selection contest to a broader one — fails here.
 *
 * <p>Docker-free (fast {@code test} lane): the exception cause chain is synthesised with the
 * exact JDBC signal each engine emits, so no real database is needed to prove the WIRING and the
 * selective predicate. The companion Testcontainers IT proves the REAL MySQL driver emits those
 * signals ({@code DataIntegrityViolationMappingIntegrationTest}).
 *
 * <p>The non-unique (23503) case is load-bearing: it proves the selective predicate did NOT
 * silence server defects by mapping every constraint violation to a client-visible 409. Finance
 * maps a unique violation to {@code CONCURRENT_MODIFICATION} (its registered code), not the
 * fleet's {@code DATA_INTEGRITY_VIOLATION}.
 */
@DisplayName("GlobalExceptionHandler DataIntegrityViolation 선별 매핑 배선 테스트")
class GlobalExceptionHandlerDataIntegrityWiringTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("unique violation (Postgres/H2 SQLSTATE 23505) → 409 CONCURRENT_MODIFICATION")
    void uniqueViolation23505_resolvesTo409() throws Exception {
        mockMvc.perform(get("/__test/unique-23505"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message").value("Data integrity conflict"));
    }

    @Test
    @DisplayName("MySQL duplicate (SQLSTATE 23000, vendor code 1062) → 409 CONCURRENT_MODIFICATION")
    void uniqueViolationMysql1062_resolvesTo409() throws Exception {
        mockMvc.perform(get("/__test/unique-mysql-1062"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message").value("Data integrity conflict"));
    }

    @Test
    @DisplayName("FK violation (SQLSTATE 23503) → 500 INTERNAL_ERROR — server defect stays loud")
    void foreignKeyViolation_stays500() throws Exception {
        mockMvc.perform(get("/__test/fk-23503"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/__test/unique-23505")
        String uniquePostgres() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint",
                    new SQLException("dup", "23505"));
        }

        @GetMapping("/__test/unique-mysql-1062")
        String uniqueMysql() {
            // MySQL groups all integrity classes under SQLSTATE 23000; the vendor code (1062)
            // is the only signal that distinguishes a duplicate from an FK / NOT NULL / CHECK.
            throw new DataIntegrityViolationException(
                    "could not execute statement",
                    new SQLException("Duplicate entry 'x' for key 'uq_...'", "23000", 1062));
        }

        @GetMapping("/__test/fk-23503")
        String fkViolation() {
            throw new DataIntegrityViolationException(
                    "insert or update violates foreign key constraint",
                    new SQLException("fk", "23503"));
        }
    }
}
