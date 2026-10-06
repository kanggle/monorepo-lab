package com.example.fanplatform.community.presentation.controller;

import com.example.common.page.PageResult;
import com.example.fanplatform.community.application.AddCommentUseCase;
import com.example.fanplatform.community.application.DeleteCommentUseCase;
import com.example.fanplatform.community.application.GetCommentsUseCase;
import com.example.fanplatform.community.application.exception.MembershipRequiredException;
import com.example.fanplatform.community.application.exception.PostNotFoundException;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.post.PostVisibility;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CommentController} (TASK-FAN-BE-002 § Tests § Slices;
 * TASK-FAN-BE-052 adds the list-path cases).
 */
@WebMvcTest(controllers = CommentController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class CommentControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AddCommentUseCase addCommentUseCase;

    @MockitoBean
    DeleteCommentUseCase deleteCommentUseCase;

    @MockitoBean
    GetCommentsUseCase getCommentsUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    @Test
    @DisplayName("POST /api/community/posts/{postId}/comments (no Authorization) → 401")
    void addComment_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/community/posts/{postId}/comments (valid) → 201 + envelope")
    void addComment_returns201_withEnvelope() throws Exception {
        Instant now = Instant.now();
        AddCommentUseCase.CommentView view = new AddCommentUseCase.CommentView(
                "comment-1", "post-1", "fan-platform", "fan-1", "Hello", now);
        when(addCommentUseCase.execute(eq("post-1"), eq("Hello"), any())).thenReturn(view);

        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").value("comment-1"))
                .andExpect(jsonPath("$.data.postId").value("post-1"))
                .andExpect(jsonPath("$.data.tenantId").value("fan-platform"))
                .andExpect(jsonPath("$.data.body").value("Hello"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/community/posts/{postId}/comments (missing post) → 404 POST_NOT_FOUND")
    void addComment_missingPost_returns404() throws Exception {
        when(addCommentUseCase.execute(eq("missing"), any(), any()))
                .thenThrow(new PostNotFoundException("missing"));

        mockMvc.perform(post("/api/community/posts/missing/comments")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/community/posts/{postId}/comments (empty body) → 422 VALIDATION_ERROR")
    void addComment_emptyBody_returns422() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /api/community/posts/{postId}/comments (no Authorization) → 401")
    void listComments_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/community/posts/post-1/comments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/community/posts/{postId}/comments (valid) → 200 + paged envelope")
    void listComments_returns200_withPagedEnvelope() throws Exception {
        Instant now = Instant.now();
        Comment c1 = Comment.create("comment-1", "fan-platform", "post-1", "fan-1", "Hello");
        PageResult<Comment> page = new PageResult<>(List.of(c1), 0, 20, 1, 1);
        when(getCommentsUseCase.execute(eq("post-1"), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/community/posts/post-1/comments")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentId").value("comment-1"))
                .andExpect(jsonPath("$.data.content[0].postId").value("post-1"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/community/posts/{postId}/comments (missing/cross-tenant post) → 404 POST_NOT_FOUND")
    void listComments_missingPost_returns404() throws Exception {
        when(getCommentsUseCase.execute(eq("missing"), any(), anyInt(), anyInt()))
                .thenThrow(new PostNotFoundException("missing"));

        mockMvc.perform(get("/api/community/posts/missing/comments")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/community/posts/{postId}/comments (gated post, no membership) → 403 MEMBERSHIP_REQUIRED")
    void listComments_gatedPostWithoutMembership_returns403() throws Exception {
        when(getCommentsUseCase.execute(eq("gated-post"), any(), anyInt(), anyInt()))
                .thenThrow(new MembershipRequiredException(PostVisibility.MEMBERS_ONLY));

        mockMvc.perform(get("/api/community/posts/gated-post/comments")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }
}
