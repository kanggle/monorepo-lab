package com.example.user;

import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.repository.WishlistItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-450 — converges {@code user-service}'s {@code DataIntegrityViolationException}
 * backstop onto the selective mapping TASK-BE-542 wired into the other eight ecommerce
 * services: a <em>unique</em> violation (SQLSTATE {@code 23505}) is a client-visible
 * conflict → 409 {@code DATA_INTEGRITY_VIOLATION}, while every other integrity violation
 * (FK / NOT NULL / CHECK) is a server defect → 500, kept loud in logs and alerting rather
 * than silently reclassified as a client 409.
 *
 * <p>This is a <b>wiring reachability</b> test, not a handler unit test: both cases drive a
 * real HTTP request through {@code WishlistController} → {@code WishlistService} → the
 * Flyway-migrated Postgres, so the {@code DataIntegrityViolationException} the assertions
 * observe is the one Spring's exception translation actually raises from a genuine
 * constraint hit — the mapped SQLSTATE is not synthesised by hand. CI Linux is authoritative;
 * local Testcontainers is FLAKY on the Windows host.
 *
 * <p><b>Why the two triggers reach the backstop deterministically.</b> {@code WishlistService.addItem}
 * pre-empts both integrity errors before they can reach the DB — a
 * {@code userProfileRepository.existsByUserId} check (→ 404 {@code USER_PROFILE_NOT_FOUND})
 * and a {@code wishlistItemRepository.existsByUserIdAndProductId} check (→ 409
 * {@code ALREADY_IN_WISHLIST}). The backstop is therefore reachable only when a pre-check's
 * verdict is stale by the time the INSERT runs, i.e. exactly the concurrency window a real
 * losing writer lands in. Each test reproduces that window by spying the relevant repository
 * so its pre-check returns the pre-race answer, then lets the <em>real</em> {@code save} hit
 * the constraint:
 * <ul>
 *   <li>409 — a competing wishlist row is already committed under the default tenant
 *       ({@code ecommerce}); the pre-check is stubbed to report "absent", so the insert
 *       collides on {@code uq_wishlist_items_tenant_user_product} (23505).</li>
 *   <li>500 — no {@code user_profiles} row exists, so the FK
 *       {@code fk_wishlist_items_user_id} (23503) rejects the insert; the profile pre-check
 *       is stubbed to "present" so the request reaches that insert instead of short-circuiting
 *       to 404.</li>
 * </ul>
 * Asserting on {@code code} — not merely the status — is what proves the response came from
 * the backstop: the pre-check paths also return 409/404 with their own domain codes.
 */
@SpringBootTest(classes = UserServiceApplication.class)
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("위시리스트 DataIntegrityViolation 백스톱 선별 매핑 통합 테스트 (TASK-MONO-450)")
class WishlistDataIntegrityBackstopIntegrationTest {

    /** Default tenant a request without X-Tenant-Id resolves to (TenantContext.DEFAULT_TENANT_ID). */
    private static final String TENANT = "ecommerce";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_db")
            .withUsername("user_user")
            .withPassword("user_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // The add path publishes no event; a dummy bootstrap address keeps the lazy
        // @Profile("!standalone") Kafka beans wired without a broker (same as
        // WishlistTenantUniquenessIntegrationTest). Both cases roll back anyway.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private WishlistItemRepository wishlistItemRepository;

    @MockitoSpyBean
    private UserProfileRepository userProfileRepository;

    @Test
    @DisplayName("유니크 위반은 선체크를 통과한 뒤 실제 23505 를 만나 409 DATA_INTEGRITY_VIOLATION 백스톱에 도달한다")
    void uniqueViolation_reachesBackstop_returns409() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        seedUserProfile(userId, "wl-backstop-409-" + System.nanoTime() + "@example.com");
        // The winner of the race is already committed under the default tenant.
        seedWishlistItem(userId, productId);

        // The pre-check ran before the competitor committed, so it reports "absent"; the losing
        // writer then collides at INSERT — the only window where the backstop is reachable.
        doReturn(false).when(wishlistItemRepository).existsByUserIdAndProductId(userId, productId);

        mockMvc.perform(post("/api/wishlists")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Data integrity violation"));
    }

    @Test
    @DisplayName("FK 위반(비-유니크)은 백스톱에서 409 로 오분류되지 않고 500 INTERNAL_ERROR 로 남는다")
    void nonUniqueViolation_reachesBackstop_returns500() throws Exception {
        // No user_profiles row for this id, so the FK fk_wishlist_items_user_id (23503) rejects
        // the insert — a non-unique integrity violation that must stay a server 500.
        UUID userIdWithoutProfile = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Bypass the USER_PROFILE_NOT_FOUND (404) pre-check so the request reaches the INSERT
        // instead of short-circuiting; the wishlist pre-check runs for real (absent → false).
        doReturn(true).when(userProfileRepository).existsByUserId(userIdWithoutProfile);

        mockMvc.perform(post("/api/wishlists")
                        .header("X-User-Id", userIdWithoutProfile.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    private void seedUserProfile(UUID userId, String email) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO user_profiles (id, tenant_id, user_id, email, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                UUID.randomUUID(), TENANT, userId, email, "백스톱테스트",
                Timestamp.from(now), Timestamp.from(now));
    }

    private void seedWishlistItem(UUID userId, UUID productId) {
        jdbcTemplate.update(
                "INSERT INTO wishlist_items (id, tenant_id, user_id, product_id, added_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), TENANT, userId, productId, Timestamp.from(Instant.now()));
    }
}
