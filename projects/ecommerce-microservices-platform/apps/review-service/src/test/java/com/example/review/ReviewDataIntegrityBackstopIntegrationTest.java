package com.example.review;

import com.example.review.application.port.PurchaseVerificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-542 AC-5 layer 2 — verifies against a REAL PostgreSQL that a genuine unique
 * violation carries SQLSTATE {@code 23505} all the way through Hibernate → Spring's
 * exception translation → {@code DataIntegrityViolations.isUniqueViolation} (promoted to
 * {@code libs/java-common} by TASK-MONO-450; the backstop handler now delegates to it). Every other
 * TASK-BE-542 test synthesises the {@code SQLException("…", "23505")} by hand and so
 * assumes exactly the fact that matters; this one does not.
 *
 * <p><b>Why this path reaches the backstop deterministically.</b> Across the eight wired
 * services, nearly every unique violation is pre-empted — either by a pre-check or by a
 * local {@code catch (DataIntegrityViolationException)} that rethrows a domain exception —
 * so the backstop is reachable only under concurrency: the losing writer passes the
 * pre-check, then collides at INSERT.
 *
 * <p><b>This test previously used a cross-tenant duplicate as its trigger</b>, which was
 * deterministic only because {@code uq_reviews_user_product_active} disagreed in scope with
 * the tenant-scoped pre-check. Its author recorded the dependency here and predicted this
 * edit: {@code TASK-BE-540} has since rebuilt the index as
 * {@code (tenant_id, user_id, product_id) WHERE status='ACTIVE'}, so a second tenant now
 * legitimately succeeds with 201 and that trigger is gone. Two reasons not to simply widen
 * the assertion: the cross-tenant state was also an <b>impossible production input</b>
 * (product ids are per-tenant UUIDs and one user id resolves to one tenant — TASK-BE-540
 * AC-0), and it required mocking away the purchase gate that would have blocked it.
 *
 * <p>The trigger is now the real one — the concurrency window itself. A competing ACTIVE row
 * is inserted from inside the mocked {@link PurchaseVerificationPort}, which
 * {@code ReviewCommandService} calls <em>after</em> the pre-check and <em>before</em>
 * {@code save}. That is exactly where the losing writer of a real race lands, so the 23505
 * this asserts is the one production can actually produce.
 *
 * <p>The assertion on {@code code} — not merely on status 409 — is what proves the response
 * came from the backstop: the pre-check path returns 409 {@code REVIEW_ALREADY_EXISTS}, so a
 * bare {@code isConflict()} could not tell the two apart.
 */
@SpringBootTest(classes = ReviewServiceApplication.class)
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@DisplayName("DataIntegrityViolation 백스톱 실제 DB 통합 테스트 (TASK-BE-542 AC-5)")
class ReviewDataIntegrityBackstopIntegrationTest {

    private static final String TENANT = "tenant-a";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("review_db")
            .withUsername("review_user")
            .withPassword("review_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("order-service.base-url", () -> "http://localhost:0");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private PurchaseVerificationPort purchaseVerificationPort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("경합에서 진 쓰기는 선체크를 통과한 뒤 실제 23505 를 만나 409 DATA_INTEGRITY_VIOLATION 이 된다")
    void lostRace_hitsBackstop_returns409DataIntegrityViolation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // The competing writer commits inside the purchase-verification call, i.e. after the
        // pre-check has already returned "absent" and before save() runs. This is the real
        // race window, not a simulation of a different defect.
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId))
                .willAnswer(invocation -> {
                    insertCompetingActiveReview(TENANT, userId, productId);
                    return true;
                });

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(productId, 4, "경합 리뷰", "경합에서 진 쓰기")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Data integrity violation"));
    }

    /** Commits a row the pre-check could not have seen — the winner of the race. */
    private void insertCompetingActiveReview(String tenantId, UUID userId, UUID productId) {
        jdbcTemplate.update("""
                INSERT INTO reviews (id, tenant_id, user_id, product_id, product_name, rating,
                                     title, content, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
                """,
                UUID.randomUUID(), tenantId, userId, productId, "상품", 5, "먼저 쓴 리뷰", "경합에서 이긴 쓰기");
    }

    @Test
    @DisplayName("같은 테넌트 중복은 선체크가 먼저 잡아 409 REVIEW_ALREADY_EXISTS 로 남는다 — 백스톱이 도메인 코드를 가리지 않는다")
    void sameTenantDuplicate_stillReturnsDomainCode() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(productId, 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(productId, 4, "중복 리뷰", "중복 리뷰 시도")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));
    }

    private String reviewJson(UUID productId, int rating, String title, String content) {
        return """
                {"productId":"%s","productName":"테스트상품","rating":%d,"title":"%s","content":"%s"}
                """.formatted(productId, rating, title, content);
    }
}
