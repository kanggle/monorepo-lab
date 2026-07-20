package com.example.fanplatform.membership.presentation.advice;

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
 * TASK-MONO-450 — proves the selective {@code DataIntegrityViolationException}
 * backstop in {@link AbstractDomainExceptionHandler} is actually REACHED (wired
 * through Spring's real {@code ExceptionHandlerExceptionResolver}), not merely
 * correct when the method is called directly. The request travels the same
 * resolution path a production request does, so a handler that was never
 * registered — or that lost the selection contest to the broader
 * {@code handleGeneral(Exception)} — fails here.
 *
 * <p>A unique violation (SQLSTATE {@code 23505}) resolves to 409 CONFLICT; every
 * other constraint class — FK {@code 23503}, NOT NULL {@code 23502} — stays 500
 * INTERNAL_ERROR, so a server defect is not silently reported as a client
 * conflict and does not vanish from logs / alerting. The non-unique cases are the
 * load-bearing ones: they prove the selective predicate did NOT keep the old
 * blanket-409 behaviour.
 *
 * <p>Mirrors ecommerce product-service's
 * {@code GlobalExceptionHandlerDataIntegrityTest} (TASK-BE-542, the selective
 * reference implementation this task converges the fleet onto).
 */
@DisplayName("GlobalExceptionHandler DataIntegrityViolation 선별 배선 테스트 (TASK-MONO-450)")
class GlobalExceptionHandlerDataIntegrityTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유니크 제약 위반(SQLSTATE 23505)이 409 CONFLICT 로 해소된다")
    void uniqueViolation_resolvesTo409() throws Exception {
        mockMvc.perform(get("/__test/unique-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Data integrity violation"));
    }

    @Test
    @DisplayName("FK 위반(SQLSTATE 23503)은 500 INTERNAL_ERROR 로 남는다 — 서버 결함이 조용해지지 않는다")
    void fkViolation_stays500() throws Exception {
        mockMvc.perform(get("/__test/fk-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    @DisplayName("NOT NULL 위반(SQLSTATE 23502)도 500 INTERNAL_ERROR 로 남는다")
    void notNullViolation_stays500() throws Exception {
        mockMvc.perform(get("/__test/not-null-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/__test/unique-violation")
        String uniqueViolation() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint",
                    new SQLException("dup", "23505"));
        }

        @GetMapping("/__test/fk-violation")
        String fkViolation() {
            throw new DataIntegrityViolationException(
                    "insert or update violates foreign key constraint",
                    new SQLException("fk", "23503"));
        }

        @GetMapping("/__test/not-null-violation")
        String notNullViolation() {
            throw new DataIntegrityViolationException(
                    "null value in column violates not-null constraint",
                    new SQLException("notnull", "23502"));
        }
    }
}
