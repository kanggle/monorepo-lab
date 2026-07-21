package com.example.user.presentation.controller;

import com.example.user.application.command.UpdateProfileCommand;
import com.example.user.application.result.UserProfileResult;
import com.example.user.application.service.UserProfileService;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.presentation.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("UserController 슬라이스 테스트")
class UserControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProfileService userProfileService;

    private static UserProfileResult createProfileResult(UUID userId) {
        return new UserProfileResult(
                userId,
                "test@example.com",
                "홍길동",
                "길동이",
                "010-1234-5678",
                "https://img.example.com/photo.jpg",
                "ACTIVE",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z")
        );
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMyProfile {

        @Test
        @DisplayName("X-User-Id 헤더로 프로필을 조회하면 200을 반환한다")
        void getMyProfile_validUserId_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            given(userProfileService.getProfile(userId)).willReturn(createProfileResult(userId));

            mockMvc.perform(get("/api/users/me")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.nickname").value("길동이"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("프로필 미존재 시 404 USER_PROFILE_NOT_FOUND를 반환한다")
        void getMyProfile_profileNotFound_returns404() throws Exception {
            UUID userId = UUID.randomUUID();
            given(userProfileService.getProfile(userId))
                    .willThrow(new UserProfileNotFoundException(userId));

            mockMvc.perform(get("/api/users/me")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401 UNAUTHORIZED를 반환한다")
        void getMyProfile_missingHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me")
    class UpdateMyProfile {

        @Test
        @DisplayName("유효한 요청으로 프로필을 수정하면 200을 반환한다")
        void updateMyProfile_validRequest_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            var result = new UserProfileResult(
                    userId, "test@example.com", "홍길동", "새닉네임",
                    "010-9999-8888", null, "ACTIVE",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-03T00:00:00Z")
            );
            given(userProfileService.updateProfile(any(UpdateProfileCommand.class))).willReturn(result);

            mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"새닉네임\",\"phone\":\"010-9999-8888\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.phone").value("010-9999-8888"));
        }

        @Test
        @DisplayName("닉네임이 50자를 초과하면 400 VALIDATION_ERROR를 반환한다")
        void updateMyProfile_nicknameTooLong_returns400() throws Exception {
            UUID userId = UUID.randomUUID();
            String longNickname = "a".repeat(51);

            mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"" + longNickname + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401 UNAUTHORIZED를 반환한다")
        void updateMyProfile_missingHeader_returns401() throws Exception {
            mockMvc.perform(patch("/api/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"닉네임\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("프로필 미존재 시 404 USER_PROFILE_NOT_FOUND를 반환한다")
        void updateMyProfile_profileNotFound_returns404() throws Exception {
            UUID userId = UUID.randomUUID();
            given(userProfileService.updateProfile(any(UpdateProfileCommand.class)))
                    .willThrow(new UserProfileNotFoundException(userId));

            mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"닉네임\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }
    }
}
