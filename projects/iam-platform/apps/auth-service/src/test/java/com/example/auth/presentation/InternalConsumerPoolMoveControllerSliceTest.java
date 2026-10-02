package com.example.auth.presentation;

import com.example.auth.application.MoveCredentialToConsumerPoolUseCase;
import com.example.auth.application.MoveCredentialToConsumerPoolUseCase.Outcome;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-618 — {@code POST /internal/auth/consumer-pool/moves} (auth-internal.md): each use-case outcome maps
 * to its status and code. The /internal/auth/** fail-closed JWT gate is {@code InternalCredentialAuthSliceTest}'s;
 * here the dev/test bypass reaches the handler (same setup as {@code InternalCredentialControllerSliceTest}).
 */
@WebMvcTest(InternalConsumerPoolMoveController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@TestPropertySource(properties = "internal.api.bypass-when-unconfigured=true")
@DisplayName("InternalConsumerPoolMoveController slice tests (TASK-BE-618)")
class InternalConsumerPoolMoveControllerSliceTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000618010";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MoveCredentialToConsumerPoolUseCase moveUseCase;

    private ResultActions move() throws Exception {
        return mockMvc.perform(post("/internal/auth/consumer-pool/moves")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"accountId\": \"" + ACCOUNT + "\", \"siteTenantId\": \"fan-platform\" }"));
    }

    @Test
    @DisplayName("MOVED → 200 {moved:true, alreadyInPool:false}")
    void moved() throws Exception {
        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.MOVED);
        move().andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(true))
                .andExpect(jsonPath("$.alreadyInPool").value(false));
    }

    @Test
    @DisplayName("ALREADY_IN_POOL → 200 {moved:false, alreadyInPool:true} (멱등)")
    void alreadyInPool() throws Exception {
        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.ALREADY_IN_POOL);
        move().andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(false))
                .andExpect(jsonPath("$.alreadyInPool").value(true));
    }

    @Test
    @DisplayName("NO_CREDENTIAL → 200 {moved:false, alreadyInPool:false}")
    void noCredential() throws Exception {
        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.NO_CREDENTIAL);
        move().andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(false))
                .andExpect(jsonPath("$.alreadyInPool").value(false));
    }

    @Test
    @DisplayName("거절 넷 → 409 POOL_MOVE_*")
    void refusals_409() throws Exception {
        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.SOCIAL_LINKED);
        move().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POOL_MOVE_SOCIAL_LINKED"));

        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.OPERATOR_FACETED);
        move().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POOL_MOVE_OPERATOR_FACETED"));

        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.POOL_CREDENTIAL_EXISTS);
        move().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POOL_MOVE_CREDENTIAL_EXISTS"));

        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.CREDENTIAL_TENANT_MISMATCH);
        move().andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POOL_MOVE_CREDENTIAL_TENANT_MISMATCH"));
    }

    @Test
    @DisplayName("FACET_UNAVAILABLE → 503 SERVICE_UNAVAILABLE (fail-closed)")
    void facetUnavailable_503() throws Exception {
        given(moveUseCase.execute(ACCOUNT, "fan-platform")).willReturn(Outcome.FACET_UNAVAILABLE);
        move().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("siteTenantId 누락 → 400 · 유스케이스 호출 없음")
    void missingSite_400() throws Exception {
        mockMvc.perform(post("/internal/auth/consumer-pool/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"accountId\": \"" + ACCOUNT + "\" }"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(moveUseCase);
    }
}
