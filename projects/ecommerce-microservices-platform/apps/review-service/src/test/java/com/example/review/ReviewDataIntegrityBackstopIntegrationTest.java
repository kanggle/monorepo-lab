package com.example.review;

import com.example.review.application.port.PurchaseVerificationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
 * exception translation → {@code GlobalExceptionHandler.isUniqueViolation}. Every other
 * TASK-BE-542 test synthesises the {@code SQLException("…", "23505")} by hand and so
 * assumes exactly the fact that matters; this one does not.
 *
 * <p><b>Why this path reaches the backstop deterministically.</b> Across the eight wired
 * services, nearly every unique violation is pre-empted — either by a pre-check or by a
 * local {@code catch (DataIntegrityViolationException)} that rethrows a domain exception —
 * so the backstop is normally reachable only under concurrency. review-service is the
 * exception, because its pre-check and its constraint disagree in scope:
 *
 * <ul>
 *   <li>pre-check {@code existsByUserIdAndProductIdAndStatusAndTenantId(…)} is
 *       <b>tenant-scoped</b>;</li>
 *   <li>the constraint {@code uq_reviews_user_product_active} — created in V2 and never
 *       rebuilt when V5 added {@code tenant_id} — is {@code (user_id, product_id) WHERE
 *       status='ACTIVE'}, with <b>no tenant column</b>.</li>
 * </ul>
 *
 * <p>So the same (user, product) pair reviewed under a second tenant passes the pre-check
 * deterministically and then violates the global index. This is the defect described by
 * {@code TASK-BE-540}; this test pins the CURRENT behaviour (the backstop converts what was
 * a 500 into a 409) and is deliberately NOT a statement that the scope mismatch is correct.
 *
 * <p><b>If TASK-BE-540 rebuilds the index to include {@code tenant_id}, this test must
 * change</b> — the second insert would then legitimately succeed with 201. That is expected
 * and is the point of recording the dependency here rather than in a commit message.
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

    @Test
    @DisplayName("테넌트가 다른 동일 (user, product) 리뷰는 전역 유니크 인덱스를 위반해 409 DATA_INTEGRITY_VIOLATION 이 된다")
    void crossTenantDuplicate_hitsBackstop_returns409DataIntegrityViolation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        given(purchaseVerificationPort.hasUserPurchasedProduct(userId, productId)).willReturn(true);

        // Tenant A: ordinary successful create.
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(productId, 5, "좋은 상품", "매우 만족합니다")))
                .andExpect(status().isCreated());

        // Tenant B: the tenant-scoped pre-check finds nothing, so the insert proceeds and
        // collides with the tenant-agnostic partial unique index → real Postgres 23505.
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", userId.toString())
                        .header("X-Tenant-Id", "tenant-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewJson(productId, 4, "다른 테넌트 리뷰", "다른 테넌트에서 작성")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.message").value("Data integrity violation"));
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
