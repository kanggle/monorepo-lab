package com.example.auth.presentation.controller;

import com.example.auth.application.dto.RepublishSignupEventsResult;
import com.example.auth.application.service.UserSignupRepublishService;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.RateLimiter;
import com.example.auth.infrastructure.config.SecurityConfig;
import com.example.auth.infrastructure.metrics.AuthMetrics;
import com.example.auth.infrastructure.security.AuthRateLimitFilter;
import com.example.auth.infrastructure.security.JsonAuthenticationEntryPoint;
import com.example.auth.infrastructure.security.JwtAuthenticationFilter;
import com.example.auth.infrastructure.security.JwtTokenParser;
import com.example.auth.presentation.advice.GlobalExceptionHandler;
import com.example.auth.presentation.support.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminUserRepublishController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class,
    JsonAuthenticationEntryPoint.class, AuthRateLimitFilter.class, ClientIpResolver.class})
@DisplayName("AdminUserRepublishController 슬라이스 테스트")
class AdminUserRepublishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserSignupRepublishService republishService;

    @MockitoBean
    private JwtTokenParser jwtTokenParser;

    @MockitoBean
    private AccessTokenBlocklist accessTokenBlocklist;

    @MockitoBean
    private RateLimiter loginRateLimiter;

    @MockitoBean
    private AuthMetrics authMetrics;

    @Test
    @DisplayName("POST /api/internal/users/republish-signup-events - 200 반환 및 집계 본문")
    void republish_returnsCounts() throws Exception {
        given(republishService.republishAll())
            .willReturn(new RepublishSignupEventsResult(152, 150, 2));

        mockMvc.perform(post("/api/internal/users/republish-signup-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").value(152))
            .andExpect(jsonPath("$.publishedCount").value(150))
            .andExpect(jsonPath("$.failedCount").value(2));
    }

    @Test
    @DisplayName("유저 0명일 때 totalUsers=0 반환")
    void republish_emptyUsers() throws Exception {
        given(republishService.republishAll())
            .willReturn(new RepublishSignupEventsResult(0, 0, 0));

        mockMvc.perform(post("/api/internal/users/republish-signup-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalUsers").value(0))
            .andExpect(jsonPath("$.publishedCount").value(0))
            .andExpect(jsonPath("$.failedCount").value(0));
    }
}
