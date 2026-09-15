package com.example.fanplatform.community.presentation.controller;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.FeedItemView;
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
        PageResult<FeedItemView> page = new PageResult<>(List.of(), 0, 20, 0L, 0);
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
        PageResult<FeedItemView> page = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(getFeedUseCase.execute(any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/community/feed")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    @DisplayName("🔴 679: 응답 항목이 mediaRefs 를 싣는다 — 열린 항목은 주소, 잠긴 항목은 빈 배열")
    void feed_itemsCarryMediaRefs_emptyWhenLocked() throws Exception {
        java.time.Instant now = java.time.Instant.parse("2026-09-15T00:00:00Z");
        FeedItemView open = new FeedItemView("p-open",
                com.example.fanplatform.community.domain.post.PostType.ARTIST_POST,
                com.example.fanplatform.community.domain.post.PostVisibility.PUBLIC,
                "artist-1", "t", "preview", List.of("https://images.example.com/a.jpg"),
                0L, 0L, now, false);
        FeedItemView locked = new FeedItemView("p-locked",
                com.example.fanplatform.community.domain.post.PostType.ARTIST_POST,
                com.example.fanplatform.community.domain.post.PostVisibility.MEMBERS_ONLY,
                "artist-1", null, null, List.of(),
                0L, 0L, now, true);
        when(getFeedUseCase.execute(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(open, locked), 0, 20, 2L, 1));

        mockMvc.perform(get("/api/community/feed").header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].mediaRefs[0]").value("https://images.example.com/a.jpg"))
                .andExpect(jsonPath("$.data.content[1].locked").value(true))
                .andExpect(jsonPath("$.data.content[1].mediaRefs").isArray())
                .andExpect(jsonPath("$.data.content[1].mediaRefs").isEmpty());
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
