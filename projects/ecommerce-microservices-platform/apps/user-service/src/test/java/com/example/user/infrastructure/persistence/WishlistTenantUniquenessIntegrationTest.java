package com.example.user.infrastructure.persistence;

import com.example.user.UserServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-546 — the wishlist unique constraint, after V6 rescoped it from
 * {@code (user_id, product_id)} to {@code (tenant_id, user_id, product_id)}, still
 * holds the invariant it stands for: one user does not wish for the same product
 * twice within a tenant.
 *
 * <p>This asserts the constraint's behaviour directly through {@link JdbcTemplate},
 * not through the {@code WishlistService} save path — the service stamps the tenant
 * from {@link com.example.user.domain.tenant.TenantContext} and could never present
 * a hand-picked {@code (tenant_id, user_id, product_id)} triple, so a persistence-layer
 * insert is the only way to exercise the key's edges. The Docker-free {@code :check}
 * slice loads no schema; this Testcontainers run against the real Flyway-migrated
 * Postgres is the authoritative check (CI Linux is authoritative — local Testcontainers
 * is FLAKY on the Windows host).
 *
 * <p><b>Why only same-tenant rejection is asserted, and not cross-tenant admission.</b>
 * AC-2's other half — "the same {@code (user_id, product_id)} under two tenants is now
 * admitted" — is deliberately <i>not</i> tested, because that row cannot be constructed:
 * {@code wishlist_items.user_id} has a FK to {@code user_profiles(user_id)}, and
 * {@code user_profiles.user_id} is globally unique, so a given {@code user_id} resolves
 * to exactly one profile and therefore one {@code tenant_id}. Two wishlist rows sharing
 * a {@code user_id} necessarily share a tenant. Fabricating the cross-tenant fixture
 * would mean inserting a {@code user_id} absent from {@code user_profiles} (a FK
 * violation) or two profiles with one {@code user_id} (a unique violation) — either way
 * proving the behaviour of a state production can never reach. That impossibility <i>is</i>
 * the reachability argument in V6's header, so the honest artefact is the same-tenant
 * invariant plus this note, not a fixture that lies (env_test_fixture_impossible_input_proves_nothing).
 */
@SpringBootTest(classes = UserServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("위시리스트 유니크 제약 — 테넌트 스코프(V6) 통합 테스트")
class WishlistTenantUniquenessIntegrationTest {

    private static final String TENANT = "tenant-a";

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
        // No event is published on this path; a dummy bootstrap address keeps the
        // lazy @Profile("!standalone") Kafka beans wired without a broker.
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID seedUser(String tenantId, String email) {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO user_profiles (id, tenant_id, user_id, email, name, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, tenantId, userId, email, "위시리스트제약테스트",
                Timestamp.from(now), Timestamp.from(now));
        return userId;
    }

    private void insertWishlist(String tenantId, UUID userId, UUID productId) {
        jdbcTemplate.update(
                "INSERT INTO wishlist_items (id, tenant_id, user_id, product_id, added_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, userId, productId, Timestamp.from(Instant.now()));
    }

    @Test
    @DisplayName("같은 테넌트 안 (user_id, product_id) 중복은 V6 이후에도 거부된다 (진짜 불변식)")
    void sameTenantDuplicate_isRejected() {
        UUID userId = seedUser(TENANT, "wl-dup-" + System.nanoTime() + "@example.com");
        UUID productId = UUID.randomUUID();

        assertThatCode(() -> insertWishlist(TENANT, userId, productId))
                .as("first wish is admitted")
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> insertWishlist(TENANT, userId, productId))
                .as("the same (user_id, product_id) in the same tenant is still a unique violation")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 사용자의 서로 다른 상품은 허용된다 (제약이 정당한 사용을 막지 않는다)")
    void sameUserDifferentProducts_areAdmitted() {
        UUID userId = seedUser(TENANT, "wl-multi-" + System.nanoTime() + "@example.com");

        assertThatCode(() -> {
            insertWishlist(TENANT, userId, UUID.randomUUID());
            insertWishlist(TENANT, userId, UUID.randomUUID());
        }).as("two different products for one user are both admitted")
                .doesNotThrowAnyException();
    }
}
