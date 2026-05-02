package com.example.community.presentation;

import com.example.community.application.FeedPage;
import com.example.community.application.GetFeedUseCase;
import com.example.community.presentation.exception.GlobalExceptionHandler;
import com.example.community.support.AccountJwtTestFixture;
import com.example.community.support.SliceTestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedController.class)
@Import({SliceTestSecurityConfig.class, GlobalExceptionHandler.class})
class FeedControllerTest {

    private static final AccountJwtTestFixture jwt;

    static {
        jwt = new AccountJwtTestFixture();
        SliceTestSecurityConfig.useFixture(jwt);
    }

    @Autowired MockMvc mockMvc;
    @MockBean GetFeedUseCase useCase;

    @Test
    void feed_returns_200_with_pagination() throws Exception {
        FeedPage page = new FeedPage(List.of(), 0, 20, 0L, 0, false);
        when(useCase.execute(any(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/community/feed?page=0&size=20")
                        .header("Authorization", "Bearer " + jwt.token("fan-1", List.of("FAN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void feed_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/api/community/feed"))
                .andExpect(status().isUnauthorized());
    }
}
