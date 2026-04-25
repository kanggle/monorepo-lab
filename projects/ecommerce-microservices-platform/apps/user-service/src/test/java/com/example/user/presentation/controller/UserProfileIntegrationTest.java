package com.example.user.presentation.controller;

import com.example.user.domain.model.ProfileStatus;
import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("사용자 프로필 API 통합 테스트")
class UserProfileIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @SuppressWarnings("resource")
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UserProfile createAndSaveProfile(UUID userId, String email, String name) {
        UserProfile profile = UserProfile.create(userId, email, name);
        return userProfileRepository.save(profile);
    }

    @Nested
    @DisplayName("GET /api/users/me")
    class GetMyProfile {

        @Test
        @DisplayName("프로필이 존재하면 200과 프로필 정보를 반환한다")
        void getMyProfile_existingProfile_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            createAndSaveProfile(userId, "integ-get@example.com", "통합테스트");

            mockMvc.perform(get("/api/users/me")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("integ-get@example.com"))
                    .andExpect(jsonPath("$.name").value("통합테스트"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("프로필이 없으면 404 USER_PROFILE_NOT_FOUND를 반환한다")
        void getMyProfile_noProfile_returns404() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(get("/api/users/me")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void getMyProfile_noHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me")
    class UpdateMyProfile {

        @Test
        @DisplayName("프로필을 수정하면 200과 수정된 정보를 반환한다")
        void updateMyProfile_validRequest_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            createAndSaveProfile(userId, "integ-patch@example.com", "수정테스트");

            mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"수정닉네임\",\"phone\":\"010-1111-2222\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("수정닉네임"))
                    .andExpect(jsonPath("$.phone").value("010-1111-2222"));

            UserProfile updated = userProfileRepository.findByUserId(userId).orElseThrow();
            assertThat(updated.getNickname()).isEqualTo("수정닉네임");
            assertThat(updated.getPhone()).isEqualTo("010-1111-2222");
        }

        @Test
        @DisplayName("부분 수정 시 null 필드는 변경되지 않는다")
        void updateMyProfile_partialUpdate_preservesExistingFields() throws Exception {
            UUID userId = UUID.randomUUID();
            UserProfile profile = createAndSaveProfile(userId, "integ-partial@example.com", "부분수정");
            profile.updateNickname("기존닉네임");
            profile.updatePhone("010-0000-0000");
            userProfileRepository.save(profile);

            mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nickname\":\"새닉네임\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.phone").value("010-0000-0000"));
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users")
    class ListUsers {

        @Test
        @DisplayName("ADMIN 권한으로 사용자 목록을 페이지네이션하여 반환한다")
        void listUsers_withAdminRole_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            createAndSaveProfile(userId, "integ-list-" + userId + "@example.com", "목록테스트");

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ADMIN")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").isNumber())
                    .andExpect(jsonPath("$.totalElements").isNumber());
        }

        @Test
        @DisplayName("ADMIN 권한 없이 요청하면 403을 반환한다")
        void listUsers_withoutAdminRole_returns403() throws Exception {
            mockMvc.perform(get("/api/admin/users"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("status 필터로 사용자 목록을 조회한다")
        void listUsers_statusFilter_returns200() throws Exception {
            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ADMIN")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("email 부분 검색으로 사용자 목록을 조회한다")
        void listUsers_emailFilter_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            createAndSaveProfile(userId, "integ-email-search@example.com", "이메일검색");

            mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ADMIN")
                            .param("email", "integ-email-search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Nested
    @DisplayName("GET /api/admin/users/{userId}")
    class GetUserById {

        @Test
        @DisplayName("ADMIN 권한으로 특정 사용자 프로필을 조회하면 200을 반환한다")
        void getUser_withAdminRole_returns200() throws Exception {
            UUID userId = UUID.randomUUID();
            createAndSaveProfile(userId, "integ-admin-get@example.com", "관리자조회");

            mockMvc.perform(get("/api/admin/users/{userId}", userId)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(userId.toString()))
                    .andExpect(jsonPath("$.email").value("integ-admin-get@example.com"));
        }

        @Test
        @DisplayName("ADMIN 권한 없이 특정 사용자 조회 시 403을 반환한다")
        void getUser_withoutAdminRole_returns403() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(get("/api/admin/users/{userId}", userId))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회 시 404를 반환한다")
        void getUser_nonExistingUser_returns404() throws Exception {
            UUID userId = UUID.randomUUID();

            mockMvc.perform(get("/api/admin/users/{userId}", userId)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }
    }
}
