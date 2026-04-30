package com.example.community.presentation;

import com.example.community.application.AddReactionUseCase;
import com.example.community.application.exception.MembershipRequiredException;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReactionController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class, ReactionControllerTest.JwtBeans.class})
class ReactionControllerTest {

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
    @MockBean AddReactionUseCase addReactionUseCase;

    private String bearer(String sub, List<String> roles) {
        return "Bearer " + jwt.token(sub, roles);
    }

    @Test
    void add_reaction_returns_200() throws Exception {
        when(addReactionUseCase.execute(eq("post-1"), eq("HEART"), any()))
                .thenReturn(new AddReactionUseCase.ReactionResult("post-1", "HEART", 5L));

        mockMvc.perform(post("/api/community/posts/post-1/reactions")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"HEART\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value("post-1"))
                .andExpect(jsonPath("$.emojiCode").value("HEART"))
                .andExpect(jsonPath("$.totalReactions").value(5));
    }

    @Test
    void add_reaction_on_members_only_without_access_returns_403() throws Exception {
        when(addReactionUseCase.execute(eq("post-m"), any(), any()))
                .thenThrow(new MembershipRequiredException());

        mockMvc.perform(post("/api/community/posts/post-m/reactions")
                        .header("Authorization", bearer("fan-free", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"FIRE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    void add_reaction_with_unsupported_emoji_returns_422() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/reactions")
                        .header("Authorization", bearer("fan-1", List.of("FAN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"ROFL\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void add_reaction_missing_auth_returns_401() throws Exception {
        mockMvc.perform(post("/api/community/posts/post-1/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emojiCode\":\"HEART\"}"))
                .andExpect(status().isUnauthorized());
    }
}
