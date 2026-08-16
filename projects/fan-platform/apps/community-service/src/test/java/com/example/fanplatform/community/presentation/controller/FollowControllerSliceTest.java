package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.FollowArtistUseCase;
import com.example.fanplatform.community.application.IsFollowingArtistUseCase;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @MockitoBean
    IsFollowingArtistUseCase isFollowingArtistUseCase;

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

    // ---- TASK-FAN-BE-049: GET /api/community/follows/{artistAccountId} -------

    @Test
    @DisplayName("GET /api/community/follows/{id} (no Authorization) → 401")
    void isFollowing_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/community/follows/artist-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 🔴 Control pair. Either half alone is passed by a constant — an
     * always-false implementation satisfies the "not following" case and an
     * always-true one satisfies the "following" case. Only the two together
     * measure anything, so they stay adjacent and are named as a pair.
     */
    @Test
    @DisplayName("GET /api/community/follows/{id} (following) → 200 following=true")
    void isFollowing_whenFollowing_returnsTrue() throws Exception {
        when(isFollowingArtistUseCase.execute(eq("artist-1"), any()))
                .thenReturn(new IsFollowingArtistUseCase.FollowStatus("artist-1", true));

        mockMvc.perform(get("/api/community/follows/artist-1")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artistAccountId").value("artist-1"))
                .andExpect(jsonPath("$.data.following").value(true))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("🔴 GET /api/community/follows/{id} (not following) → 200 following=false, NOT 404")
    void isFollowing_whenNotFollowing_returns200False_not404() throws Exception {
        when(isFollowingArtistUseCase.execute(eq("artist-2"), any()))
                .thenReturn(new IsFollowingArtistUseCase.FollowStatus("artist-2", false));

        mockMvc.perform(get("/api/community/follows/artist-2")
                        .header("Authorization", fanBearer("fan-1")))
                // The sibling DELETE answers 404 NOT_FOLLOWING for this same
                // condition. Copying that here would make "not following" and
                // "no such artist" the same response — see community-api.md.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artistAccountId").value("artist-2"))
                .andExpect(jsonPath("$.data.following").value(false));
    }
}
