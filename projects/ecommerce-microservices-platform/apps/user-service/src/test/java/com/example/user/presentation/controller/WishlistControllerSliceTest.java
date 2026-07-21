package com.example.user.presentation.controller;

import com.example.user.application.result.AddWishlistItemResult;
import com.example.user.application.result.WishlistCheckResult;
import com.example.user.application.result.WishlistItemResult;
import com.example.user.application.result.WishlistPageResult;
import com.example.user.application.service.WishlistService;
import com.example.user.domain.exception.AlreadyInWishlistException;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.exception.WishlistAccessDeniedException;
import com.example.user.domain.exception.WishlistItemNotFoundException;
import com.example.user.presentation.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WishlistController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("WishlistController 슬라이스 테스트")
class WishlistControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WishlistService wishlistService;

    private static final UUID USER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("POST /api/wishlists")
    class AddItem {

        @Test
        @DisplayName("위시리스트에 상품을 추가하면 201과 항목 정보를 반환한다")
        void addItem_validRequest_returns201() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID wishlistItemId = UUID.randomUUID();
            var result = new AddWishlistItemResult(wishlistItemId, productId);
            given(wishlistService.addItem(any())).willReturn(result);

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.wishlistItemId").value(wishlistItemId.toString()))
                    .andExpect(jsonPath("$.productId").value(productId.toString()));
        }

        @Test
        @DisplayName("productId가 없으면 400을 반환한다")
        void addItem_missingProductId_returns400() throws Exception {
            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":null}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("이미 위시리스트에 있는 상품을 추가하면 409를 반환한다")
        void addItem_duplicate_returns409() throws Exception {
            UUID productId = UUID.randomUUID();
            given(wishlistService.addItem(any())).willThrow(new AlreadyInWishlistException(productId));

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_IN_WISHLIST"));
        }

        @Test
        @DisplayName("user_profiles 행이 없으면 404 USER_PROFILE_NOT_FOUND를 반환한다")
        void addItem_userProfileNotFound_returns404() throws Exception {
            UUID productId = UUID.randomUUID();
            given(wishlistService.addItem(any())).willThrow(new UserProfileNotFoundException(USER_ID));

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));
        }

        @Test
        @DisplayName("unique 위반(SQLSTATE 23505)은 409 DATA_INTEGRITY_VIOLATION을 반환한다")
        void addItem_uniqueViolation_returns409() throws Exception {
            UUID productId = UUID.randomUUID();
            // A duplicate is a client-visible conflict — the backstop's 409 case (TASK-MONO-450 selective mapping).
            DataIntegrityViolationException unique = new DataIntegrityViolationException(
                    "duplicate key", new java.sql.SQLException("duplicate key value", "23505"));
            given(wishlistService.addItem(any())).willThrow(unique);

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
        }

        @Test
        @DisplayName("non-unique 무결성 위반(FK/NOT NULL)은 500 INTERNAL_ERROR을 반환한다 — 서버 결함은 숨기지 않는다")
        void addItem_nonUniqueViolation_returns500() throws Exception {
            UUID productId = UUID.randomUUID();
            // An FK / NOT NULL violation is a server defect, not a client conflict: it must stay a loud 500
            // rather than be masked as a 409 (TASK-MONO-450). No SQLSTATE 23505 in the chain → not unique.
            DataIntegrityViolationException fk = new DataIntegrityViolationException(
                    "FK violation", new java.sql.SQLException("foreign key violation", "23503"));
            given(wishlistService.addItem(any())).willThrow(fk);

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void addItem_missingHeader_returns401() throws Exception {
            mockMvc.perform(post("/api/wishlists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + UUID.randomUUID() + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("productId가 UUID 형식이 아니면 400 VALIDATION_ERROR를 반환한다")
        void addItem_nonUuidProductId_returns400() throws Exception {
            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"mock-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
        }

        @Test
        @DisplayName("요청 본문이 깨진 JSON이면 400 VALIDATION_ERROR를 반환한다")
        void addItem_malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", USER_ID.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message").value("Malformed request body"));
        }
    }

    @Nested
    @DisplayName("GET /api/wishlists/me")
    class GetWishlist {

        @Test
        @DisplayName("위시리스트 목록을 조회하면 200과 페이지 결과를 반환한다")
        void getWishlist_valid_returns200() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID wishlistItemId = UUID.randomUUID();
            var item = new WishlistItemResult(wishlistItemId, productId, "테스트상품", 15000, "ACTIVE", Instant.now());
            var pageResult = new WishlistPageResult(List.of(item), 0, 20, 1);
            given(wishlistService.getWishlist(eq(USER_ID), eq(0), eq(20))).willReturn(pageResult);

            mockMvc.perform(get("/api/wishlists/me")
                            .header("X-User-Id", USER_ID.toString())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].wishlistItemId").value(wishlistItemId.toString()))
                    .andExpect(jsonPath("$.content[0].productName").value("테스트상품"))
                    .andExpect(jsonPath("$.content[0].productPrice").value(15000))
                    .andExpect(jsonPath("$.content[0].productStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void getWishlist_missingHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/wishlists/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/wishlists/{wishlistItemId}")
    class RemoveItem {

        @Test
        @DisplayName("위시리스트 항목을 삭제하면 204를 반환한다")
        void removeItem_valid_returns204() throws Exception {
            UUID wishlistItemId = UUID.randomUUID();

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", wishlistItemId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("존재하지 않는 항목을 삭제하면 404를 반환한다")
        void removeItem_notFound_returns404() throws Exception {
            UUID wishlistItemId = UUID.randomUUID();
            willThrow(new WishlistItemNotFoundException(wishlistItemId))
                    .given(wishlistService).removeItem(eq(USER_ID), eq(wishlistItemId));

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", wishlistItemId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("WISHLIST_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자의 항목을 삭제하면 403을 반환한다")
        void removeItem_accessDenied_returns403() throws Exception {
            UUID wishlistItemId = UUID.randomUUID();
            willThrow(new WishlistAccessDeniedException(wishlistItemId))
                    .given(wishlistService).removeItem(eq(USER_ID), eq(wishlistItemId));

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", wishlistItemId)
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void removeItem_missingHeader_returns401() throws Exception {
            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("GET /api/wishlists/me/check")
    class CheckItem {

        @Test
        @DisplayName("위시리스트에 있는 상품을 체크하면 200과 inWishlist=true, wishlistItemId를 반환한다")
        void checkItem_exists_returnsTrue() throws Exception {
            UUID productId = UUID.randomUUID();
            UUID wishlistItemId = UUID.randomUUID();
            var result = new WishlistCheckResult(productId, true, wishlistItemId);
            given(wishlistService.checkItem(eq(USER_ID), eq(productId))).willReturn(result);

            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", USER_ID.toString())
                            .param("productId", productId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.inWishlist").value(true))
                    .andExpect(jsonPath("$.wishlistItemId").value(wishlistItemId.toString()));
        }

        @Test
        @DisplayName("위시리스트에 없는 상품을 체크하면 200과 inWishlist=false, wishlistItemId=null을 반환한다")
        void checkItem_notExists_returnsFalse() throws Exception {
            UUID productId = UUID.randomUUID();
            var result = new WishlistCheckResult(productId, false, null);
            given(wishlistService.checkItem(eq(USER_ID), eq(productId))).willReturn(result);

            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", USER_ID.toString())
                            .param("productId", productId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inWishlist").value(false))
                    .andExpect(jsonPath("$.wishlistItemId").value(nullValue()));
        }

        @Test
        @DisplayName("productId 파라미터 없이 요청하면 400을 반환한다")
        void checkItem_missingProductId_returns400() throws Exception {
            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", USER_ID.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("X-User-Id 헤더 없이 요청하면 401을 반환한다")
        void checkItem_missingHeader_returns401() throws Exception {
            mockMvc.perform(get("/api/wishlists/me/check")
                            .param("productId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }
}
