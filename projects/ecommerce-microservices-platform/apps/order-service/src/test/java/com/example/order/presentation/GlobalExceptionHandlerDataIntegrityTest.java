package com.example.order.presentation;

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
 * Proves the TASK-BE-542 DataIntegrityViolationException handler is actually REACHED
 * (AC-5), not merely that it computes the right value when called directly. The request
 * travels through Spring's real ExceptionHandlerExceptionResolver, so a handler that was
 * never registered — or that lost the selection contest to a broader one — fails here.
 *
 * <p>The 23503 case is the load-bearing one: it proves the selective predicate did NOT
 * silence server defects by mapping every constraint violation to a client-visible 409.
 */
@DisplayName("GlobalExceptionHandler DataIntegrityViolationException 배선 테스트")
class GlobalExceptionHandlerDataIntegrityTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("유니크 제약 위반(SQLSTATE 23505)이 409 DATA_INTEGRITY_VIOLATION 으로 해소된다")
    void uniqueViolation_resolvesTo409() throws Exception {
        mockMvc.perform(get("/__test/unique-violation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Data integrity violation"));
    }

    @Test
    @DisplayName("FK 위반(SQLSTATE 23503)은 500 INTERNAL_ERROR 로 남는다 — 서버 결함이 조용해지지 않는다")
    void nonUniqueViolation_stays500() throws Exception {
        mockMvc.perform(get("/__test/fk-violation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
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
    }
}
