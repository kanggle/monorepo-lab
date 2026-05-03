package com.example.fanplatform.community.presentation.controller;

import com.example.fanplatform.community.application.AddReactionUseCase;
import com.example.fanplatform.community.application.RemoveReactionUseCase;
import com.example.fanplatform.community.domain.reaction.ReactionType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link ReactionController} (TASK-FAN-BE-002 § Tests § Slices).
 */
@WebMvcTest(controllers = ReactionController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class ReactionControllerSliceTest {

    private static final JwtTestHelper jwt;

    static {
        jwt = new JwtTestHelper();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AddReactionUseCase addReactionUseCase;

    @MockitoBean
    RemoveReactionUseCase removeReactionUseCase;

    private String fanBearer(String sub) {
        return "Bearer " + jwt.signFanToken(sub);
    }

    @Test
    @DisplayName("PUT /api/community/posts/{postId}/reactions (no Authorization) → 401")
    void putReaction_withoutAuth_returns401() throws Exception {
        mockMvc.perform(put("/api/community/posts/post-1/reactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reactionType\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PUT /api/community/posts/{postId}/reactions (LIKE) twice — both 200 OK + envelope")
    void putReaction_idempotent_bothReturn200() throws Exception {
        when(addReactionUseCase.execute(eq("post-1"), eq(ReactionType.LIKE), any()))
                .thenReturn(new AddReactionUseCase.ReactionResult("post-1", ReactionType.LIKE, 1L));

        mockMvc.perform(put("/api/community/posts/post-1/reactions")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reactionType\":\"LIKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value("post-1"))
                .andExpect(jsonPath("$.data.reactionType").value("LIKE"))
                .andExpect(jsonPath("$.data.totalReactions").value(1))
                .andExpect(jsonPath("$.meta.timestamp").exists());

        // Second identical call → still 200; controller delegates to the use
        // case which itself short-circuits the duplicate save (verified by
        // AddReactionUseCaseTest). Slice-level we only assert the HTTP shape.
        mockMvc.perform(put("/api/community/posts/post-1/reactions")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reactionType\":\"LIKE\"}"))
                .andExpect(status().isOk());

        verify(addReactionUseCase, times(2))
                .execute(eq("post-1"), eq(ReactionType.LIKE), any());
    }

    @Test
    @DisplayName("DELETE /api/community/posts/{postId}/reactions → 204 NO_CONTENT")
    void deleteReaction_returns204() throws Exception {
        mockMvc.perform(delete("/api/community/posts/post-1/reactions")
                        .header("Authorization", fanBearer("fan-1")))
                .andExpect(status().isNoContent());

        verify(removeReactionUseCase).execute(eq("post-1"), any());
    }

    @Test
    @DisplayName("PUT /api/community/posts/{postId}/reactions (invalid reactionType) → 4xx (validation)")
    void putReaction_invalidType_returns4xx() throws Exception {
        // The DTO uses an enum field. Jackson treats unknown enum values as
        // a parsing failure — Spring maps that to a 400 BAD_REQUEST via the
        // HttpMessageNotReadableException handler in GlobalExceptionHandler.
        mockMvc.perform(put("/api/community/posts/post-1/reactions")
                        .header("Authorization", fanBearer("fan-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reactionType\":\"BOGUS\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
