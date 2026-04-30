package com.example.auth.presentation;

import com.example.auth.application.CreateCredentialUseCase;
import com.example.auth.application.ForceLogoutUseCase;
import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.presentation.exception.AuthExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalCredentialController.class)
@Import({SecurityConfig.class, AuthExceptionHandler.class})
@DisplayName("InternalCredentialController slice tests")
class InternalCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCredentialUseCase createCredentialUseCase;

    @MockitoBean
    private ForceLogoutUseCase forceLogoutUseCase;

    @Test
    @DisplayName("POST /internal/auth/credentials happy path returns 201")
    void createCredential_returns201() throws Exception {
        Instant createdAt = Instant.parse("2026-04-19T10:00:00Z");
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willReturn(new CreateCredentialResult("acc-1", createdAt));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-1"))
                .andExpect(jsonPath("$.createdAt").value("2026-04-19T10:00:00Z"));

        ArgumentCaptor<CreateCredentialCommand> captor =
                ArgumentCaptor.forClass(CreateCredentialCommand.class);
        verify(createCredentialUseCase).execute(captor.capture());
        CreateCredentialCommand cmd = captor.getValue();
        assertThat(cmd.accountId()).isEqualTo("acc-1");
        assertThat(cmd.email()).isEqualTo("user@example.com");
        assertThat(cmd.password()).isEqualTo("password123");
    }

    @Test
    @DisplayName("POST /internal/auth/credentials with duplicate accountId returns 409 CREDENTIAL_ALREADY_EXISTS")
    void duplicateAccountReturns409() throws Exception {
        given(createCredentialUseCase.execute(any(CreateCredentialCommand.class)))
                .willThrow(new CredentialAlreadyExistsException("acc-1"));

        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Missing accountId → 400 VALIDATION_ERROR")
    void missingAccountIdReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Invalid email format → 400 VALIDATION_ERROR")
    void invalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "not-an-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Password shorter than 8 chars → 400 VALIDATION_ERROR")
    void shortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "acc-1",
                                  "email": "user@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Malformed JSON → 400 VALIDATION_ERROR")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
