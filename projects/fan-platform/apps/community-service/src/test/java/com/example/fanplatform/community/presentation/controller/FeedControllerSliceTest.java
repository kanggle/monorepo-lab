package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.FeedPage;
import com.example.fanplatform.community.application.GetFeedUseCase;
import com.example.fanplatform.community.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.community.testsupport.JwtTestHelper;
import com.example.fanplatform.community.testsupport.SliceTestSecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link FeedController} (TASK-FAN-BE-002 § Tests § Slices).
 */
@WebMvcTest(controllers = FeedController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class FeedControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GetFeedUseCase getFeedUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    @Test
    @DisplayName("GET /api/community/feed (no Authorization) → 401")
    void feed_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/community/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/community/feed (empty follows) → 200 + empty content + envelope")
    void feed_emptyResult_returns200() throws Exception {
        FeedPage page = new FeedPage(List.of(), 0, 20, 0L, 0, false);
        when(getFeedUseCase.execute(any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/community/feed?page=0&size=20")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/community/feed (default page/size when omitted) → 200 with defaults page=0 size=20")
    void feed_defaultPagination() throws Exception {
        FeedPage page = new FeedPage(List.of(), 0, 20, 0L, 0, false);
        when(getFeedUseCase.execute(any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("GET /api/community/feed?page=abc → 400 VALIDATION_ERROR (type mismatch)")
    void feed_invalidPaginationType_returns400() throws Exception {
        mockMvc.perform(get("/api/community/feed?page=abc&size=20")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
