package com.example.community.presentation;

import com.example.community.application.AddCommentUseCase;
import com.example.community.application.exception.MembershipRequiredException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.presentation.exception.GlobalExceptionHandler;
import com.example.community.support.AccountJwtTestFixture;
import com.example.community.support.SliceTestSecurityConfig;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommentController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class, CommentControllerTest.JwtBeans.class})
class CommentControllerTest {

    private static AccountJwtTestFixture jwt;

    @BeforeAll
    static void init() { jwt = new AccountJwtTestFixture(); }

    @TestConfiguration
    static class JwtBeans {
        @Bean JwtVerifier communityJwtVerifier() {
            if (jwt == null) jwt = new AccountJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockBean AddCommentUseCase addCommentUseCase;

    private String bearer(String sub, List<String> roles) {
        return "Bearer " + jwt.token(sub, roles);
    }

    @Test
    void add_comment_returns_201() throws Exception {
        var view = new AddCommentUseCase.CommentView(
                "c-1", "post-1", "fan-1", "Fan", "Nice!", Instant.now());
        when(addCommentUseCase.execute(eq("post-1"), any(), any())).thenReturn(view);

        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Nice!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").value("c-1"))
                .andExpect(jsonPath("$.postId").value("post-1"));
    }

    @Test
    void add_comment_on_members_only_without_access_returns_403() throws Exception {
        when(addCommentUseCase.execute(eq("post-m"), any(), any()))
                .thenThrow(new MembershipRequiredException());

        mockMvc.perform(post("/api/community/posts/post-m/comments")
                        .header("Authorization", bearer("fan-free", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    void add_comment_to_missing_post_returns_404() throws Exception {
        when(addCommentUseCase.execute(eq("missing"), any(), any()))
                .thenThrow(new PostNotFoundException("missing"));

        mockMvc.perform(post("/api/community/posts/missing/comments")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void add_comment_missing_auth_returns_401() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_comment_with_empty_body_returns_422() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/comments")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
