package com.example.user.contract;

import com.example.user.application.result.AddressResult;
import com.example.user.application.result.UserProfileResult;
import com.example.user.application.result.UserProfileSummaryResult;
import com.example.user.application.service.AddressService;
import com.example.user.application.service.UserProfileService;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.presentation.exception.GlobalExceptionHandler;
import com.example.user.presentation.controller.AddressController;
import com.example.user.presentation.controller.AdminUserController;
import com.example.user.presentation.controller.UserController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.example.user.application.result.UserListPageResult;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.example.user.contract.ContractTestHelper.assertFieldsMatch;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * user-service API 응답 스키마 컨트랙트 검증 테스트.
 * 검증 근거: specs/contracts/http/user-api.md
 */
@WebMvcTest({UserController.class, AddressController.class, AdminUserController.class})
@Import(GlobalExceptionHandler.class)
@DisplayName("User API 컨트랙트 테스트 — specs/contracts/http/user-api.md")
class UserApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private AddressService addressService;

    private static final String SPEC_REF = "specs/contracts/http/user-api.md";
    private static final UUID USER_ID = UUID.randomUUID();

    // ─── GET /api/users/me — 200 ────────────────────────────────────────

    @Nested
    @DisplayName("프로필 API")
    class ProfileApi {

        @Test
        @DisplayName("GET /api/users/me 응답은 스펙 정의 필드만 포함한다")
        void getProfile_response_containsSpecFields() throws Exception {
            given(userProfileService.getProfile(any())).willReturn(profileResult());

            MvcResult result = mockMvc.perform(get("/api/users/me")
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            assertFieldsMatch(result.getResponse().getContentAsString(),
                    Set.of("userId", "email", "name", "nickname", "phone", "profileImageUrl", "status", "createdAt", "updatedAt"),
                    SPEC_REF + " GET /api/users/me 200");
        }

        @Test
        @DisplayName("PATCH /api/users/me 응답은 스펙 정의 필드만 포함한다")
        void updateProfile_response_containsSpecFields() throws Exception {
            given(userProfileService.updateProfile(any())).willReturn(profileResult());

            MvcResult result = mockMvc.perform(patch("/api/users/me")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("nickname", "새닉네임"))))
                    .andExpect(status().isOk())
                    .andReturn();

            assertFieldsMatch(result.getResponse().getContentAsString(),
                    Set.of("userId", "email", "name", "nickname", "phone", "profileImageUrl", "status", "updatedAt"),
                    SPEC_REF + " PATCH /api/users/me 200");
        }
    }

    // ─── Address API ────────────────────────────────────────────────────

    @Nested
    @DisplayName("주소 API")
    class AddressApi {

        @Test
        @DisplayName("GET /api/users/me/addresses 응답은 {addresses}만 포함한다")
        void getAddresses_response_containsSpecFields() throws Exception {
            AddressResult addr = new AddressResult(UUID.randomUUID(), "집", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", "역삼동", true);
            given(addressService.getAddresses(any())).willReturn(List.of(addr));

            MvcResult result = mockMvc.perform(get("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            assertFieldsMatch(json, Set.of("addresses"), SPEC_REF + " GET /api/users/me/addresses 200");

            JsonNode addrNode = objectMapper.readTree(json).get("addresses").get(0);
            assertFieldsMatch(addrNode, Set.of("id", "label", "recipientName", "phone", "zipCode", "address1", "address2", "isDefault"),
                    SPEC_REF + " GET /api/users/me/addresses 200 addresses[]");
        }

        @Test
        @DisplayName("POST /api/users/me/addresses 응답은 {id}만 포함한다")
        void createAddress_response_containsSpecFields() throws Exception {
            UUID addressId = UUID.randomUUID();
            given(addressService.createAddress(any())).willReturn(addressId);

            MvcResult result = mockMvc.perform(post("/api/users/me/addresses")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "label", "집", "recipientName", "홍길동", "phone", "010-1234-5678",
                                    "zipCode", "12345", "address1", "서울시 강남구"))))
                    .andExpect(status().isCreated())
                    .andReturn();

            assertFieldsMatch(result.getResponse().getContentAsString(),
                    Set.of("id"), SPEC_REF + " POST /api/users/me/addresses 201");
        }

        @Test
        @DisplayName("PATCH /api/users/me/addresses/{addressId} 응답은 {id}만 포함한다")
        void updateAddress_response_containsSpecFields() throws Exception {
            UUID addressId = UUID.randomUUID();
            AddressResult addrResult = new AddressResult(addressId, "회사", "홍길동", "010-1234-5678",
                    "12345", "서울시 강남구", "역삼동", false);
            given(addressService.updateAddress(any())).willReturn(addrResult);

            MvcResult result = mockMvc.perform(patch("/api/users/me/addresses/" + addressId)
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("label", "회사"))))
                    .andExpect(status().isOk())
                    .andReturn();

            assertFieldsMatch(result.getResponse().getContentAsString(),
                    Set.of("id"), SPEC_REF + " PATCH /api/users/me/addresses/{addressId} 200");
        }
    }

    // ─── Admin API ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("관리자 API")
    class AdminApi {

        @Test
        @DisplayName("GET /api/admin/users 응답은 {content, page, size, totalElements}만 포함한다")
        void listUsers_response_containsSpecFields() throws Exception {
            UserProfileSummaryResult summary = new UserProfileSummaryResult(
                    UUID.randomUUID(), "test@example.com", "홍길동", "길동이", "ACTIVE", Instant.now());
            given(userProfileService.listUsers(isNull(), isNull(), eq(0), eq(20)))
                    .willReturn(new UserListPageResult(List.of(summary), 1L, 1, 0, 20));

            MvcResult result = mockMvc.perform(get("/api/admin/users")
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andReturn();

            String json = result.getResponse().getContentAsString();
            assertFieldsMatch(json, Set.of("content", "page", "size", "totalElements"),
                    SPEC_REF + " GET /api/admin/users 200");

            JsonNode item = objectMapper.readTree(json).get("content").get(0);
            assertFieldsMatch(item, Set.of("userId", "email", "name", "nickname", "status", "createdAt"),
                    SPEC_REF + " GET /api/admin/users 200 content[]");
        }

        @Test
        @DisplayName("GET /api/admin/users/{userId} 응답은 스펙 정의 필드만 포함한다")
        void getUser_response_containsSpecFields() throws Exception {
            given(userProfileService.getProfile(any())).willReturn(profileResult());

            MvcResult result = mockMvc.perform(get("/api/admin/users/" + USER_ID)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertFieldsMatch(result.getResponse().getContentAsString(),
                    Set.of("userId", "email", "name", "nickname", "phone", "profileImageUrl", "status", "createdAt", "updatedAt"),
                    SPEC_REF + " GET /api/admin/users/{userId} 200");
        }
    }

    // ─── Error Response Format ──────────────────────────────────────────

    @Test
    @DisplayName("에러 응답은 {code, message, timestamp}만 포함한다")
    void errorResponse_containsOnlyCodeMessageTimestamp() throws Exception {
        given(userProfileService.getProfile(any())).willThrow(new UserProfileNotFoundException(USER_ID));

        MvcResult result = mockMvc.perform(get("/api/users/me")
                        .header("X-User-Id", USER_ID.toString()))
                .andExpect(status().isNotFound())
                .andReturn();

        assertFieldsMatch(result.getResponse().getContentAsString(),
                Set.of("code", "message", "timestamp"),
                "specs/platform/error-handling.md error format");
    }

    private UserProfileResult profileResult() {
        return new UserProfileResult(
                USER_ID, "test@example.com", "홍길동", "길동이",
                "010-1234-5678", "https://img.example.com/photo.jpg",
                "ACTIVE", Instant.now(), Instant.now());
    }
}
