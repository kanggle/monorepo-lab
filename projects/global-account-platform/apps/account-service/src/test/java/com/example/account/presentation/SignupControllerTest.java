package com.example.account.presentation;

import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.SignupResult;
import com.example.account.application.service.SignupUseCase;
import com.example.account.infrastructure.config.SecurityConfig;
import com.example.account.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SignupController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("SignupController 슬라이스 테스트")
class SignupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignupUseCase signupUseCase;

    @Test
    @DisplayName("유효한 가입 요청 시 201 반환")
    void signup_validRequest_returns201() throws Exception {
        given(signupUseCase.execute(any())).willReturn(
                new SignupResult("acc-123", "test@example.com", "ACTIVE", Instant.now()));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-123"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("이메일 누락 시 400 VALIDATION_ERROR 반환")
    void signup_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("패스워드 짧을 때 400 VALIDATION_ERROR 반환")
    void signup_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("중복 이메일 시 409 ACCOUNT_ALREADY_EXISTS 반환")
    void signup_duplicateEmail_returns409() throws Exception {
        given(signupUseCase.execute(any())).willThrow(
                new AccountAlreadyExistsException("test@example.com"));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("잘못된 JSON 요청 시 400 반환")
    void signup_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // TASK-BE-065: AuthServiceUnavailable 이 5xx / timeout / CB-open 을 승격한 경우
    // GlobalExceptionHandler 가 503 + AUTH_SERVICE_UNAVAILABLE 로 매핑해야 한다.
    @Test
    @DisplayName("auth-service 불가 시 503 AUTH_SERVICE_UNAVAILABLE 반환")
    void signup_authServiceUnavailable_returns503() throws Exception {
        given(signupUseCase.execute(any())).willThrow(
                new AuthServicePort.AuthServiceUnavailable("auth-service is unavailable",
                        new RuntimeException("connection refused")));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com",
                                  "password": "Password1!"
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Authentication service is temporarily unavailable"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
