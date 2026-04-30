package com.example.account.presentation;

import com.example.account.application.result.AccountMeResult;
import com.example.account.application.result.ProfileUpdateResult;
import com.example.account.application.service.ProfileUseCase;
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
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProfileController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("ProfileController 슬라이스 테스트")
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileUseCase profileUseCase;

    @Test
    @DisplayName("GET /api/accounts/me 성공 시 200 반환")
    void getMe_validRequest_returns200() throws Exception {
        var profileResult = new AccountMeResult.ProfileResult(
                "홍길동", "010-****-5678", LocalDate.of(1990, 1, 1),
                "ko-KR", "Asia/Seoul", null);
        var result = new AccountMeResult("acc-123", "test@example.com", "ACTIVE",
                profileResult, Instant.now());

        given(profileUseCase.getMe(eq("acc-123"))).willReturn(result);

        mockMvc.perform(get("/api/accounts/me")
                        .header("X-Account-Id", "acc-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-123"))
                .andExpect(jsonPath("$.profile.phoneNumber").value("010-****-5678"));
    }

    @Test
    @DisplayName("X-Account-Id 헤더 누락 시 400 반환")
    void getMe_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /api/accounts/me/profile 성공 시 200 반환")
    void updateProfile_validRequest_returns200() throws Exception {
        var result = new ProfileUpdateResult(
                "새이름", "010-****-5678", LocalDate.of(1990, 1, 1),
                "ko-KR", "Asia/Seoul", null);

        given(profileUseCase.updateProfile(any())).willReturn(result);

        mockMvc.perform(patch("/api/accounts/me/profile")
                        .header("X-Account-Id", "acc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "새이름"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("새이름"));
    }
}
