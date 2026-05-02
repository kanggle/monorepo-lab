package com.example.community.presentation;

import com.example.community.application.FollowArtistUseCase;
import com.example.community.application.exception.AlreadyFollowingException;
import com.example.community.application.exception.NotFollowingException;
import com.example.community.presentation.exception.GlobalExceptionHandler;
import com.example.community.support.AccountJwtTestFixture;
import com.example.community.support.SliceTestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedSubscriptionController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class FeedSubscriptionControllerTest {

    private static final AccountJwtTestFixture jwt;

    static {
        jwt = new AccountJwtTestFixture();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockBean FollowArtistUseCase followArtistUseCase;

    private String bearer(String sub, List<String> roles) {
        return "Bearer " + jwt.token(sub, roles);
    }

    @Test
    void follow_artist_returns_200() throws Exception {
        when(followArtistUseCase.follow(eq("fan-1"), eq("artist-1")))
                .thenReturn(new FollowArtistUseCase.FollowResult("fan-1", "artist-1", Instant.now()));

        mockMvc.perform(post("/api/community/subscriptions/artists/artist-1")
                        .header("Authorization", bearer("fan-1", List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fanAccountId").value("fan-1"))
                .andExpect(jsonPath("$.artistAccountId").value("artist-1"));
    }

    @Test
    void follow_already_following_returns_409() throws Exception {
        when(followArtistUseCase.follow(eq("fan-1"), eq("artist-1")))
                .thenThrow(new AlreadyFollowingException());

        mockMvc.perform(post("/api/community/subscriptions/artists/artist-1")
                        .header("Authorization", bearer("fan-1", List.of("FAN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_FOLLOWING"));
    }

    @Test
    void unfollow_returns_204() throws Exception {
        mockMvc.perform(delete("/api/community/subscriptions/artists/artist-1")
                        .header("Authorization", bearer("fan-1", List.of("FAN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void unfollow_not_following_returns_404() throws Exception {
        doThrow(new NotFollowingException())
                .when(followArtistUseCase).unfollow(eq("fan-1"), eq("artist-1"));

        mockMvc.perform(delete("/api/community/subscriptions/artists/artist-1")
                        .header("Authorization", bearer("fan-1", List.of("FAN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOLLOWING"));
    }

    @Test
    void follow_missing_auth_returns_401() throws Exception {
        mockMvc.perform(post("/api/community/subscriptions/artists/artist-1"))
                .andExpect(status().isUnauthorized());
    }
}
