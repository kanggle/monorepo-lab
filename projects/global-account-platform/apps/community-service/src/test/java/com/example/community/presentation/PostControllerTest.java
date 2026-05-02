package com.example.community.presentation;

import com.example.community.application.ChangePostStatusUseCase;
import com.example.community.application.GetPostUseCase;
import com.example.community.application.PostView;
import com.example.community.application.PublishPostCommand;
import com.example.community.application.PublishPostUseCase;
import com.example.community.application.UpdatePostResponse;
import com.example.community.application.UpdatePostUseCase;
import com.example.community.application.exception.PermissionDeniedException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.post.PostType;
import com.example.community.domain.post.PostVisibility;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.presentation.exception.GlobalExceptionHandler;
import com.example.community.support.AccountJwtTestFixture;
import com.example.community.support.SliceTestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PostController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class PostControllerTest {

    private static final AccountJwtTestFixture jwt;

    static {
        jwt = new AccountJwtTestFixture();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PublishPostUseCase publishPostUseCase;

    @MockBean
    GetPostUseCase getPostUseCase;

    @MockBean
    UpdatePostUseCase updatePostUseCase;

    @MockBean
    ChangePostStatusUseCase changePostStatusUseCase;

    private String bearer(String sub, List<String> roles) {
        return "Bearer " + jwt.token(sub, roles);
    }

    // ─── existing tests ────────────────────────────────────────────────────────

    @Test
    void publish_post_as_artist_returns_201() throws Exception {
        PostView view = new PostView(
                "post-1", PostType.ARTIST_POST, PostVisibility.PUBLIC, PostStatus.PUBLISHED,
                "artist-1", "Artist", "t", "body",
                0L, 0L, null, Instant.now(), Instant.now());
        when(publishPostUseCase.execute(any(PublishPostCommand.class))).thenReturn(view);

        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC","title":"t","body":"hello"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value("post-1"))
                .andExpect(jsonPath("$.type").value("ARTIST_POST"))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void publish_artist_post_as_fan_returns_403() throws Exception {
        when(publishPostUseCase.execute(any(PublishPostCommand.class)))
                .thenThrow(new PermissionDeniedException("ARTIST role required"));

        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC","body":"hello"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void missing_authorization_returns_401() throws Exception {
        mockMvc.perform(post("/api/community/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FAN_POST\",\"visibility\":\"PUBLIC\",\"body\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void cross_tenant_token_returns_403() throws Exception {
        // TASK-BE-253: tokens with tenant_id != fan-platform must be rejected.
        String wmsToken = "Bearer " + jwt.tokenWithTenant("wms-user", List.of("ARTIST"), "wms");

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", wmsToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"FAN_POST\",\"visibility\":\"PUBLIC\",\"body\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    void sas_issued_token_passes() throws Exception {
        // TASK-BE-253: tokens issued by SAS (iss=http://localhost:8081) also pass.
        PostView view = new PostView(
                "post-2", PostType.ARTIST_POST, PostVisibility.PUBLIC, PostStatus.PUBLISHED,
                "artist-2", "Artist", "t", "body",
                0L, 0L, null, Instant.now(), Instant.now());
        when(publishPostUseCase.execute(any(PublishPostCommand.class))).thenReturn(view);

        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC","title":"t","body":"hello"}
                """;

        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", "Bearer " + jwt.sasToken("artist-2", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void missing_body_returns_422() throws Exception {
        String body = """
                {"type":"ARTIST_POST","visibility":"PUBLIC"}
                """;
        mockMvc.perform(post("/api/community/posts")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ─── PATCH /{postId} ───────────────────────────────────────────────────────

    @Test
    void patchPost_authorUpdatesContent_returns200() throws Exception {
        Instant now = Instant.now();
        UpdatePostResponse response = new UpdatePostResponse(
                "post-1", "New Title", "New body", List.of("https://cdn.example.com/img.jpg"), now);
        when(updatePostUseCase.execute(eq("post-1"), any(), any(), any(), any()))
                .thenReturn(response);

        String requestBody = """
                {"title":"New Title","body":"New body","mediaUrls":["https://cdn.example.com/img.jpg"]}
                """;

        mockMvc.perform(patch("/api/community/posts/post-1")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value("post-1"))
                .andExpect(jsonPath("$.title").value("New Title"))
                .andExpect(jsonPath("$.body").value("New body"));
    }

    @Test
    void patchPost_nonAuthor_returns403() throws Exception {
        when(updatePostUseCase.execute(eq("post-1"), any(), any(), any(), any()))
                .thenThrow(new PermissionDeniedException("Only the author can update this post"));

        String requestBody = """
                {"title":"Hacked title","body":"Hacked body"}
                """;

        mockMvc.perform(patch("/api/community/posts/post-1")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void patchPost_notFound_returns404() throws Exception {
        when(updatePostUseCase.execute(eq("missing"), any(), any(), any(), any()))
                .thenThrow(new PostNotFoundException("missing"));

        String requestBody = """
                {"body":"Some body"}
                """;

        mockMvc.perform(patch("/api/community/posts/missing")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ─── PATCH /{postId}/status ───────────────────────────────────────────────

    @Test
    void patchStatus_publishDraft_returns204() throws Exception {
        doNothing().when(changePostStatusUseCase).execute(
                eq("post-1"), any(), any(), any(), any());

        String requestBody = """
                {"status":"PUBLISHED"}
                """;

        mockMvc.perform(patch("/api/community/posts/post-1/status")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNoContent());
    }

    @Test
    void patchStatus_invalidTransition_returns422() throws Exception {
        doThrow(new IllegalStateException("STATE_TRANSITION_INVALID"))
                .when(changePostStatusUseCase).execute(eq("post-deleted"), any(), any(), any(), any());

        String requestBody = """
                {"status":"PUBLISHED","reason":"trying to restore deleted post"}
                """;

        mockMvc.perform(patch("/api/community/posts/post-deleted/status")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POST_STATUS_TRANSITION_INVALID"));
    }

    @Test
    void patchStatus_notFound_returns404() throws Exception {
        doThrow(new PostNotFoundException("missing"))
                .when(changePostStatusUseCase).execute(eq("missing"), any(), any(), any(), any());

        String requestBody = """
                {"status":"PUBLISHED"}
                """;

        mockMvc.perform(patch("/api/community/posts/missing/status")
                        .header("Authorization", bearer("artist-1", List.of("ARTIST")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}
