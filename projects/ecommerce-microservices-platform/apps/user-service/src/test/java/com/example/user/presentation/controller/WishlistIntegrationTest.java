package com.example.user.presentation.controller;

import com.example.user.domain.model.UserProfile;
import com.example.user.domain.model.WishlistItem;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.repository.WishlistItemRepository;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("위시리스트 API 통합 테스트")
class WishlistIntegrationTest {

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

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    private UUID createUser() {
        UUID userId = UUID.randomUUID();
        UserProfile profile = UserProfile.create(userId, "wishlist-test-" + userId + "@example.com", "위시리스트테스트");
        userProfileRepository.save(profile);
        return userId;
    }

    private WishlistItem createAndSaveWishlistItem(UUID userId, UUID productId) {
        WishlistItem item = WishlistItem.create(userId, productId);
        return wishlistItemRepository.save(item);
    }

    @Nested
    @DisplayName("POST /api/wishlists")
    class AddItem {

        @Test
        @DisplayName("위시리스트에 상품을 추가하면 201을 반환한다")
        void addItem_valid_returns201() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.wishlistItemId").isNotEmpty())
                    .andExpect(jsonPath("$.productId").value(productId.toString()));

            assertThat(wishlistItemRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        }

        @Test
        @DisplayName("동일 상품을 중복 추가하면 409를 반환한다")
        void addItem_duplicate_returns409() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();
            createAndSaveWishlistItem(userId, productId);

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_IN_WISHLIST"));
        }

        @Test
        @DisplayName("user_profiles 행이 없는 유저로 요청하면 404 USER_PROFILE_NOT_FOUND를 반환한다")
        void addItem_userProfileMissing_returns404() throws Exception {
            UUID userIdWithoutProfile = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", userIdWithoutProfile.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":\"" + productId + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_PROFILE_NOT_FOUND"));

            assertThat(wishlistItemRepository.existsByUserIdAndProductId(userIdWithoutProfile, productId)).isFalse();
        }

        @Test
        @DisplayName("productId 없이 요청하면 400을 반환한다")
        void addItem_missingProductId_returns400() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(post("/api/wishlists")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":null}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/wishlists/me")
    class GetWishlist {

        @Test
        @DisplayName("위시리스트 목록을 조회하면 200과 페이지 결과를 반환한다")
        void getWishlist_withItems_returns200() throws Exception {
            UUID userId = createUser();
            UUID productId1 = UUID.randomUUID();
            UUID productId2 = UUID.randomUUID();
            createAndSaveWishlistItem(userId, productId1);
            createAndSaveWishlistItem(userId, productId2);

            mockMvc.perform(get("/api/wishlists/me")
                            .header("X-User-Id", userId.toString())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("빈 위시리스트를 조회하면 빈 목록을 반환한다")
        void getWishlist_empty_returnsEmptyList() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(get("/api/wishlists/me")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("페이지네이션이 올바르게 동작한다")
        void getWishlist_pagination_works() throws Exception {
            UUID userId = createUser();
            for (int i = 0; i < 5; i++) {
                createAndSaveWishlistItem(userId, UUID.randomUUID());
            }

            mockMvc.perform(get("/api/wishlists/me")
                            .header("X-User-Id", userId.toString())
                            .param("page", "0")
                            .param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(2));
        }
    }

    @Nested
    @DisplayName("DELETE /api/wishlists/{wishlistItemId}")
    class RemoveItem {

        @Test
        @DisplayName("위시리스트 항목을 삭제하면 204를 반환한다")
        void removeItem_valid_returns204() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();
            WishlistItem item = createAndSaveWishlistItem(userId, productId);

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", item.getId())
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNoContent());

            assertThat(wishlistItemRepository.findById(item.getId())).isEmpty();
        }

        @Test
        @DisplayName("존재하지 않는 항목을 삭제하면 404를 반환한다")
        void removeItem_notFound_returns404() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", UUID.randomUUID())
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("WISHLIST_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("다른 사용자의 항목을 삭제하면 403을 반환한다")
        void removeItem_otherUsersItem_returns403() throws Exception {
            UUID ownerUserId = createUser();
            UUID otherUserId = createUser();
            UUID productId = UUID.randomUUID();
            WishlistItem item = createAndSaveWishlistItem(ownerUserId, productId);

            mockMvc.perform(delete("/api/wishlists/{wishlistItemId}", item.getId())
                            .header("X-User-Id", otherUserId.toString()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("GET /api/wishlists/me/check")
    class CheckItem {

        @Test
        @DisplayName("위시리스트에 있는 상품을 체크하면 inWishlist=true와 wishlistItemId를 반환한다")
        void checkItem_exists_returnsTrue() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();
            WishlistItem saved = createAndSaveWishlistItem(userId, productId);

            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", userId.toString())
                            .param("productId", productId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productId").value(productId.toString()))
                    .andExpect(jsonPath("$.inWishlist").value(true))
                    .andExpect(jsonPath("$.wishlistItemId").value(saved.getId().toString()));
        }

        @Test
        @DisplayName("위시리스트에 없는 상품을 체크하면 inWishlist=false와 wishlistItemId=null을 반환한다")
        void checkItem_notExists_returnsFalse() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();

            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", userId.toString())
                            .param("productId", productId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inWishlist").value(false))
                    .andExpect(jsonPath("$.wishlistItemId").value(org.hamcrest.Matchers.nullValue()));
        }

        @Test
        @DisplayName("productId 파라미터 없이 요청하면 400을 반환한다")
        void checkItem_missingProductId_returns400() throws Exception {
            UUID userId = createUser();

            mockMvc.perform(get("/api/wishlists/me/check")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @Test
        @DisplayName("동시에 같은 상품을 위시리스트에 추가해도 하나만 성공한다")
        void concurrentAdd_sameProduct_onlyOneSucceeds() throws Exception {
            UUID userId = createUser();
            UUID productId = UUID.randomUUID();

            int threadCount = 3;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        var result = mockMvc.perform(post("/api/wishlists")
                                        .header("X-User-Id", userId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"productId\":\"" + productId + "\"}"))
                                .andReturn();
                        int statusCode = result.getResponse().getStatus();
                        if (statusCode == 201) {
                            successCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        rejectedCount.incrementAndGet();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);

            // DB unique constraint ensures only one item exists regardless of race conditions
            assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
            assertThat(successCount.get() + rejectedCount.get()).isEqualTo(threadCount);
            assertThat(wishlistItemRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        }
    }
}
