package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.FollowArtistUseCase;
import com.example.fanplatform.community.application.UnfollowArtistUseCase;
import com.example.fanplatform.community.application.exception.SelfFollowForbiddenException;
import com.example.fanplatform.community.presentation.advice.GlobalExceptionHandler;
import com.example.fanplatform.community.testsupport.JwtTestHelper;
import com.example.fanplatform.community.testsupport.SliceTestSecurityConfig;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link FollowController} (TASK-FAN-BE-002 § Tests § Slices).
 */
@WebMvcTest(controllers = FollowController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class FollowControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FollowArtistUseCase followArtistUseCase;

    @MockitoBean
    UnfollowArtistUseCase unfollowArtistUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    @Test
    @DisplayName("POST /api/community/follows (no Authorization) → 401")
    void follow_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/community/follows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistAccountId\":\"artist-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/community/follows (artistId == fanId) → 422 SELF_FOLLOW_FORBIDDEN")
    void follow_self_returns422() throws Exception {
        when(followArtistUseCase.execute(eq("fan-1"), any()))
                .thenThrow(new SelfFollowForbiddenException());

        mockMvc.perform(post("/api/community/follows")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistAccountId\":\"fan-1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_FOLLOW_FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/community/follows (valid) → 201 + envelope")
    void follow_returns201_withEnvelope() throws Exception {
        Instant now = Instant.now();
        FollowArtistUseCase.FollowResult result = new FollowArtistUseCase.FollowResult(
                "fan-1", "artist-1", "fan-platform", now);
        when(followArtistUseCase.execute(eq("artist-1"), any())).thenReturn(result);

        mockMvc.perform(post("/api/community/follows")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"artistAccountId\":\"artist-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fanAccountId").value("fan-1"))
                .andExpect(jsonPath("$.data.artistAccountId").value("artist-1"))
                .andExpect(jsonPath("$.data.tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("DELETE /api/community/follows/{artistAccountId} → 204 NO_CONTENT")
    void unfollow_returns204() throws Exception {
        mockMvc.perform(delete("/api/community/follows/artist-1")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isNoContent());

        verify(unfollowArtistUseCase).execute(eq("artist-1"), any());
    }
}
