package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.ChangePostStatusUseCase;
import com.example.fanplatform.community.application.DeletePostUseCase;
import com.example.fanplatform.community.application.GetMyPostsUseCase;
import com.example.fanplatform.community.application.GetPostUseCase;
import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.PostView;
import com.example.fanplatform.community.application.PublishPostCommand;
import com.example.fanplatform.community.application.PublishPostUseCase;
import com.example.fanplatform.community.application.UpdatePostUseCase;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.ActorType;
import com.example.fanplatform.community.domain.post.status.InvalidStateTransitionException;
import com.example.fanplatform.community.domain.post.status.PostStatus;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link PostController} (TASK-FAN-BE-002 § Tests § Slices).
 *
 * <p>Boots only the controller + {@link GlobalExceptionHandler} +
 * {@link SliceTestSecurityConfig} (a stand-alone Resource Server filter chain
 * wired to the per-test {@link JwtTestHelper}). The {@code TenantClaimEnforcer}
 * filter is intentionally excluded — it's exercised separately by
 * {@code TenantClaimEnforcerTest}.
 */
@WebMvcTest(controllers = PostController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class PostControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PublishPostUseCase publishPostUseCase;

    @MockitoBean
    GetPostUseCase getPostUseCase;

    @MockitoBean
    GetMyPostsUseCase getMyPostsUseCase;

    @MockitoBean
    UpdatePostUseCase updatePostUseCase;

    @MockitoBean
    ChangePostStatusUseCase changePostStatusUseCase;

    @MockitoBean
    DeletePostUseCase deletePostUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    private String artistBearer(String sub) {
        return "Bearer " + jwt.signArtistToken(sub);
    }

    @Test
    @DisplayName("POST /api/community/posts (no Authorization) → 401 UNAUTHORIZED")
    void publish_withoutAuth_returns401() throws Exception {
        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC","body":"hello"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/community/posts (artist, valid) → 201 + envelope { data, meta }")
    void publish_artist_returns201_withEnvelope() throws Exception {
        Instant now = Instant.now();
        PostView view = new PostView(
                "post-1", "fan-platform",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, PostStatus.PUBLISHED,
                "artist-1", "t", "body", 0L, 0L, now, now, now);
        when(publishPostUseCase.execute(any(PublishPostCommand.class))).thenReturn(view);

        String body = """
                {"postType":"ARTIST_POST","visibility":"PUBLIC","title":"t","body":"hello"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", artistBearer("artist-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.postId").value("post-1"))
                .andExpect(jsonPath("$.data.tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.data.postType").value("ARTIST_POST"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/community/posts (missing body field) → 422 VALIDATION_ERROR")
    void publish_missingBody_returns422() throws Exception {
        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("PATCH /api/community/posts/{id}/status — PUBLISHED→DRAFT 거부 → 422 POST_STATUS_TRANSITION_INVALID")
    void changeStatus_publishedToDraft_returns422() throws Exception {
        doThrow(new InvalidStateTransitionException(PostStatus.PUBLISHED, PostStatus.DRAFT, ActorType.AUTHOR))
                .when(changePostStatusUseCase).execute(eq("post-1"), eq(PostStatus.DRAFT), any(), any());

        String body = """
                {"status":"DRAFT"}
                """;

        mockMvc.perform(patch("/api/community/posts/post-1/status")
                        .header("Authorization", artistBearer("artist-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POST_STATUS_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("PUBLISHED"))
                .andExpect(jsonPath("$.details.to").value("DRAFT"));
    }

    @Test
    @DisplayName("PATCH /api/community/posts/{id}/status — missing post → 404 POST_NOT_FOUND")
    void changeStatus_missingPost_returns404() throws Exception {
        doThrow(new PostNotFoundException("missing"))
                .when(changePostStatusUseCase).execute(eq("missing"), any(), any(), any());

        String body = """
                {"status":"PUBLISHED"}
                """;

        mockMvc.perform(patch("/api/community/posts/missing/status")
                        .header("Authorization", artistBearer("artist-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("cross-tenant 토큰 (tenant_id=wms) → 403 TENANT_FORBIDDEN")
    void publish_crossTenant_returns403() throws Exception {
        String wmsToken = "Bearer " + jwt.signCrossTenantToken("operator-1");

        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC","body":"hi"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", wmsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    // ------------------------------------------------------------------
    // GET /mine (TASK-FAN-FE-016)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/community/posts/mine (no Authorization) → 401")
    void mine_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/community/posts/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/community/posts/mine → 200 + paged envelope")
    void mine_returnsPagedEnvelope() throws Exception {
        Instant now = Instant.now();
        PostView view = new PostView(
                "post-9", "fan-platform",
                PostType.FAN_POST, PostVisibility.PUBLIC, PostStatus.PUBLISHED,
                "fan-1", "my title", "my body", 2L, 3L, now, now, now);
        when(getMyPostsUseCase.execute(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(java.util.List.of(view), 0, 20, 1, 1));

        mockMvc.perform(get("/api/community/posts/mine").header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value("post-9"))
                .andExpect(jsonPath("$.data.content[0].title").value("my title"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("🔴 GET /mine 은 GET /{postId} 로 새지 않는다 — 'mine' 이 postId 로 읽히면 안 된다")
    void mine_isNotRoutedAsAPostIdLookup() throws Exception {
        when(getMyPostsUseCase.execute(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/community/posts/mine").header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk());

        // Spring ranks the literal segment above the path variable, but nothing in the code
        // says so — a later refactor to @GetMapping("/{postId:.+}") or a rename would flip it
        // silently, and the symptom (404, or an empty list) reads like "no posts yet".
        org.mockito.Mockito.verify(getPostUseCase, org.mockito.Mockito.never())
                .execute(eq("mine"), any());
    }

    @Test
    @DisplayName("GET /mine?page=2&size=5 → 파라미터가 유스케이스로 그대로 전달된다")
    void mine_passesPagingParams() throws Exception {
        when(getMyPostsUseCase.execute(any(), eq(2), eq(5)))
                .thenReturn(new PageResult<>(java.util.List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/community/posts/mine")
                        .param("page", "2").param("size", "5")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(getMyPostsUseCase).execute(any(), eq(2), eq(5));
    }
}
