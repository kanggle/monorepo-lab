package com.example.auth.presentation;

import com.example.auth.application.SecondFactorEnrolmentStatusQuery;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-771 S5 — {@code POST /internal/auth/second-factor/enrolment-status} (admin-to-auth.md). The
 * /internal/auth/** fail-closed JWT gate is {@code InternalCredentialAuthSliceTest}'s; here the dev/test bypass
 * reaches the handler (same setup as {@code InternalConsumerPoolMoveControllerSliceTest}).
 */
@WebMvcTest(InternalSecondFactorStatusController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("InternalSecondFactorStatusController slice tests (TASK-MONO-771 S5)")
class InternalSecondFactorStatusControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecondFactorEnrolmentStatusQuery query;

    @Test
    @DisplayName("200 — enrolledAccountIds is exactly what the query answered")
    void answersEnrolledSubset() throws Exception {
        given(query.enrolledAmong(List.of("acc-1", "acc-2", "acc-3"))).willReturn(Set.of("acc-2"));
        mockMvc.perform(post("/internal/auth/second-factor/enrolment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"acc-1\",\"acc-2\",\"acc-3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledAccountIds.length()").value(1))
                .andExpect(jsonPath("$.enrolledAccountIds[0]").value("acc-2"));
    }

    @Test
    @DisplayName("missing accountIds → 400 VALIDATION_ERROR, query never asked")
    void missingIds_400() throws Exception {
        mockMvc.perform(post("/internal/auth/second-factor/enrolment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(query);
    }

    @Test
    @DisplayName("over the per-call cap (query refuses) → 400 VALIDATION_ERROR, never a silent truncation")
    void overCap_400() throws Exception {
        given(query.enrolledAmong(List.of("x"))).willThrow(new IllegalArgumentException("At most 500 accountIds per call"));
        mockMvc.perform(post("/internal/auth/second-factor/enrolment-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountIds\":[\"x\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
