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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static final String PHOTO = "https://images.example.com/photo-1.jpg";

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

    private static PostView view(String id, List<String> mediaRefs) {
        Instant now = Instant.now();
        return new PostView(
                id, "fan-platform",
                PostType.ARTIST_POST, PostVisibility.PUBLIC, PostStatus.PUBLISHED,
                "artist-1", "t", "body", mediaRefs, 0L, 0L, now, now, now);
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
        when(publishPostUseCase.execute(any(PublishPostCommand.class))).thenReturn(view("post-1", List.of()));

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
                // 🔵 사진 0장은 빈 배열이다 — 키가 빠진 것과 화면이 가르지 않게 (TASK-MONO-679).
                .andExpect(jsonPath("$.data.mediaRefs").isArray())
                .andExpect(jsonPath("$.data.mediaRefs").isEmpty())
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

    // ------------------------------------------------------------------
    // mediaRefs = absolute https URLs (TASK-MONO-679 ⓐ)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("🔴 POST mediaRefs 가 https 절대주소가 아니면 422 — 저장소 키·http·스킴 없는 주소를 저장하지 않는다")
    void publish_nonHttpsMediaRef_returns422_andNeverReachesTheUseCase() throws Exception {
        String[] rejected = {
                "s3://fan-media/posts/a.jpg",       // 옛 계약 예시의 «키» — 이제는 거부한다
                "http://minio.local/fan/a.jpg",     // https 페이지에서 mixed content 로 깨진다
                "//images.example.com/a.jpg",       // 스킴 없음
                "fan/posts/a.jpg",                  // 상대 경로(키 모양)
                "https://",                         // 호스트 없음
                "https://images.example.com/a b.jpg" // 공백
        };
        for (String bad : rejected) {
            String body = """
                    {"postType":"FAN_POST","visibility":"PUBLIC","body":"hello","mediaRefs":["%s"]}
                    """.formatted(bad);
            mockMvc.perform(post("/api/community/posts")
                            .header("Authorization", fanBearer("fan-1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verify(publishPostUseCase, never()).execute(any(PublishPostCommand.class));
    }

    @Test
    @DisplayName("🔵 대조군: https 절대주소는 통과하고 그대로 유스케이스에 닿으며 응답에 실린다")
    void publish_httpsMediaRef_isAccepted_andEchoed() throws Exception {
        when(publishPostUseCase.execute(any(PublishPostCommand.class))).thenReturn(view("post-2", List.of(PHOTO)));

        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC","body":"hello","mediaRefs":["%s"]}
                """.formatted(PHOTO);

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mediaRefs[0]").value(PHOTO));

        verify(publishPostUseCase).execute(argThat(cmd -> List.of(PHOTO).equals(cmd.mediaRefs())));
    }

    @Test
    @DisplayName("POST mediaRefs 가 10장을 넘으면 422")
    void publish_tooManyMediaRefs_returns422() throws Exception {
        StringBuilder refs = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) refs.append(',');
            refs.append("\"https://images.example.com/").append(i).append(".jpg\"");
        }
        String body = """
                {"postType":"FAN_POST","visibility":"PUBLIC","body":"hello","mediaRefs":[%s]}
                """.formatted(refs);

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("🔴 PATCH 도 같은 규칙이다 — 발행 뒤 수정으로 키를 밀어 넣을 수 없다")
    void update_nonHttpsMediaRef_returns422() throws Exception {
        String body = """
                {"body":"edited","mediaRefs":["s3://fan-media/posts/a.jpg"]}
                """;

        mockMvc.perform(patch("/api/community/posts/post-1")
                        .header("Authorization", artistBearer("artist-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
                "fan-1", "my title", "my body", List.of(PHOTO), 2L, 3L, now, now, now);
        when(getMyPostsUseCase.execute(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(view), 0, 20, 1, 1));

        mockMvc.perform(get("/api/community/posts/mine").header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].postId").value("post-9"))
                .andExpect(jsonPath("$.data.content[0].title").value("my title"))
                .andExpect(jsonPath("$.data.content[0].mediaRefs[0]").value(PHOTO))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("🔴 GET /mine 은 GET /{postId} 로 새지 않는다 — 'mine' 이 postId 로 읽히면 안 된다")
    void mine_isNotRoutedAsAPostIdLookup() throws Exception {
        when(getMyPostsUseCase.execute(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/community/posts/mine").header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk());

        // Spring ranks the literal segment above the path variable, but nothing in the code
        // says so — a later refactor to @GetMapping("/{postId:.+}") or a rename would flip it
        // silently, and the symptom (404, or an empty list) reads like "no posts yet".
        verify(getPostUseCase, never()).execute(eq("mine"), any());
    }

    @Test
    @DisplayName("GET /mine?page=2&size=5 → 파라미터가 유스케이스로 그대로 전달된다")
    void mine_passesPagingParams() throws Exception {
        when(getMyPostsUseCase.execute(any(), eq(2), eq(5)))
                .thenReturn(new PageResult<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/community/posts/mine")
                        .param("page", "2").param("size", "5")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk());

        verify(getMyPostsUseCase).execute(any(), eq(2), eq(5));
    }
}
