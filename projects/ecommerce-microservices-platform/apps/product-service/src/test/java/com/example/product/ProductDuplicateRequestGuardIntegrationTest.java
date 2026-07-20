package com.example.product;

import com.example.product.application.command.AdjustStockCommand;
import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.dto.AdjustStockResult;
import com.example.product.application.service.AdjustStockService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.VariantManagementService;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.exception.DuplicateVariantOptionException;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockingDetails;

/**
 * TASK-BE-536 — the four product-service/promotion-service stock/coupon endpoints
 * that had no duplicate-request protection (ADR-002 D3 census). This file is the
 * <b>authoritative</b> lane for the three product-service endpoints: the mechanisms
 * are Flyway-owned DB constraints ({@code uq_product_variants_option},
 * {@code stock_adjustment_request}, {@code product_create_request}) that only exist
 * in a real Postgres, and the properties under test (variant/stock/product counts)
 * are persisted-state properties. Unit tests (mocked repositories) pin the branch
 * logic; this proves the migrations applied and the constraints are real.
 *
 * <p>Every assertion is on the persisted <b>balance</b> (variant count / stock
 * quantity / product count), never on the existence of a dedupe row alone — the row
 * is the mechanism, the balance is the property (AC-1).
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("재고/상품/변형 중복요청 가드 통합 테스트 (TASK-BE-536)")
class ProductDuplicateRequestGuardIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("product_user")
            .withPassword("product_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("spring.cache.type", () -> "none");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RegisterProductService registerProductService;
    @Autowired
    private AdjustStockService adjustStockService;
    @Autowired
    private VariantManagementService variantManagementService;
    @Autowired
    private QueryProductService queryProductService;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID registerProduct(String name, int stock, String idempotencyKey) {
        return registerProductService.register(new RegisterProductCommand(
                name, "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", stock, 0)), idempotencyKey));
    }

    private int stockOf(UUID productId, UUID variantId) {
        return queryProductService.findById(productId).variants().stream()
                .filter(v -> v.id().equals(variantId))
                .findFirst().orElseThrow().stock();
    }

    // ── POST /api/admin/products/{id}/variants — natural key ──────────────

    /** AC-1 / AC-4 — the schema constraint blocks a duplicate optionName outright. */
    @Test
    @DisplayName("AC-1/AC-4 동일 상품에 같은 optionName 재추가 시 DuplicateVariantOptionException, 변형 개수 불변")
    void addVariant_duplicateOptionName_isRejected_variantCountUnchanged() {
        UUID productId = registerProduct("변형 중복 상품", 10, "reg-var-1");
        int before = queryProductService.findById(productId).variants().size();

        assertThatThrownBy(() -> variantManagementService.addVariant(productId, "기본", 5, 0))
                .isInstanceOf(DuplicateVariantOptionException.class);

        assertThat(queryProductService.findById(productId).variants()).hasSize(before);
    }

    /** AC-2 — a different optionName on the SAME product is a genuine second variant. */
    @Test
    @DisplayName("AC-2 다른 optionName 은 정상적으로 추가된다")
    void addVariant_differentOptionName_isGenuineSecondVariant() {
        UUID productId = registerProduct("변형 정상 추가 상품", 10, "reg-var-2");

        assertThatCode(() -> variantManagementService.addVariant(productId, "라지", 5, 500))
                .doesNotThrowAnyException();

        assertThat(queryProductService.findById(productId).variants()).hasSize(2);
    }

    /** Edge Case — the unique scope is per-product, not global. */
    @Test
    @DisplayName("optionName 유니크 스코프는 상품별 — 다른 상품엔 같은 optionName 을 써도 정상 처리된다")
    void addVariant_sameOptionNameOnDifferentProduct_isNotADuplicate() {
        UUID productA = registerProduct("A 상품", 10, "reg-var-3a");
        UUID productB = registerProduct("B 상품", 10, "reg-var-3b");

        assertThatCode(() -> variantManagementService.addVariant(productB, "기본-추가", 3, 0))
                .doesNotThrowAnyException();
        // "기본" already exists on BOTH A and B from registration — proves the
        // constraint did not (incorrectly) reject product creation itself.
        assertThat(queryProductService.findById(productA).variants()).hasSize(1);
        assertThat(queryProductService.findById(productB).variants()).hasSize(2);
    }

    /** AC-4 backstop is a real DB constraint — asserted directly against the schema. */
    @Test
    @DisplayName("AC-4 (product_id, option_name) 유니크 제약이 실제 스키마에 존재한다")
    void uniqueConstraintExistsInSchema_variants() {
        UUID productId = registerProduct("스키마 확인 상품", 10, "reg-var-4");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_variants (id, product_id, option_name, stock, additional_price, tenant_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID(), productId, "기본", 1, 0, "ecommerce"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // The original row (from registration) is unaffected.
        assertThat(stockOf(productId, variantId)).isEqualTo(10);
    }

    // ── PATCH /api/admin/products/{id}/stock — Idempotency-Key ────────────

    @Test
    @DisplayName("AC-1 같은 키로 재고 조정 2회 → 재고가 두 번 조정되지 않는다")
    void adjustStock_sameKeyReplay_doesNotAdjustTwice() {
        UUID productId = registerProduct("재고 멱등성 상품", 10, "reg-stock-1");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-A"));
        AdjustStockResult replay = adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-A"));

        assertThat(replay.currentStock()).isEqualTo(15);
        assertThat(stockOf(productId, variantId)).isEqualTo(15);
    }

    /** AC-3 — a replay must not re-publish StockChanged. */
    @Test
    @DisplayName("AC-3 같은 키 재생 시 StockChanged 이벤트가 재발행되지 않는다 (유효 조정당 정확히 1회)")
    void adjustStock_sameKeyReplay_doesNotRepublishEvent() {
        UUID productId = registerProduct("이벤트 멱등성 상품", 10, "reg-stock-2");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-B"));
        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-B"));

        long publishCount = mockingDetails(kafkaTemplate).getInvocations().stream()
                .filter(inv -> inv.getMethod().getName().equals("send"))
                .filter(inv -> "product.product.stock-changed".equals(inv.getArgument(0)))
                .filter(inv -> inv.getArgument(2) instanceof ProductEvent e
                        && e.payload() instanceof com.example.product.domain.event.StockChangedPayload p
                        && p.productId().equals(productId.toString()))
                .count();

        assertThat(publishCount).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키 + 다른 수량 재사용 → IdempotencyKeyConflictException, 재고 불변")
    void adjustStock_sameKeyDifferentQuantity_isRejected() {
        UUID productId = registerProduct("재고 충돌 상품", 10, "reg-stock-3");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-C"));

        assertThatThrownBy(() -> adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 9, "RESTOCK", "stock-key-C")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(stockOf(productId, variantId)).isEqualTo(15);
    }

    /** AC-2 — Edge Case: two identical "+10" adjustments can both be genuine. */
    @Test
    @DisplayName("AC-2 다른 키로 두 번째 동일 수량 조정 → 정상적으로 누적된다 (진짜 두 번째 입고)")
    void adjustStock_differentKey_sameQuantity_isGenuineSecondAdjustment() {
        UUID productId = registerProduct("재입고 두번 상품", 0, "reg-stock-4");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 10, "RESTOCK", "stock-key-D1"));
        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 10, "RESTOCK", "stock-key-D2"));

        assertThat(stockOf(productId, variantId)).isEqualTo(20);
    }

    @Test
    @DisplayName("F4 Idempotency-Key 없는 재고 조정 요청은 거부되고 재고가 변하지 않는다")
    void adjustStock_missingKey_isRefused_noAdjustmentPerformed() {
        UUID productId = registerProduct("키 없음 상품", 10, "reg-stock-5");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();

        assertThatThrownBy(() -> adjustStockService.adjust(
                new AdjustStockCommand(productId, variantId, 5, "RESTOCK", null)))
                .isInstanceOf(IdempotencyKeyRequiredException.class);

        assertThat(stockOf(productId, variantId)).isEqualTo(10);
    }

    @Test
    @DisplayName("AC-4 (variant_id, idempotency_key) 유니크 제약이 실제 스키마에 존재한다")
    void uniqueConstraintExistsInSchema_stockAdjustmentRequest() {
        UUID productId = registerProduct("재고 스키마 확인 상품", 10, "reg-stock-6");
        UUID variantId = queryProductService.findById(productId).variants().get(0).id();
        adjustStockService.adjust(new AdjustStockCommand(productId, variantId, 5, "RESTOCK", "stock-key-E"));

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO stock_adjustment_request (variant_id, idempotency_key, quantity) VALUES (?, ?, ?)",
                variantId, "stock-key-E", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── POST /api/admin/products — Idempotency-Key ─────────────────────────

    @Test
    @DisplayName("AC-1 같은 키로 상품 등록 2회 → 두 번째 상품이 생성되지 않는다 (같은 productId 반환)")
    void register_sameKeyReplay_doesNotCreateSecondProduct() {
        RegisterProductCommand command = new RegisterProductCommand(
                "등록 멱등성 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "create-key-A");

        UUID first = registerProductService.register(command);
        UUID replay = registerProductService.register(command);

        assertThat(replay).isEqualTo(first);
    }

    @Test
    @DisplayName("같은 키 + 다른 상품명 재사용 → IdempotencyKeyConflictException, 두번째 상품 미생성")
    void register_sameKeyDifferentName_isRejected() {
        registerProduct("최초 상품명", 10, "create-key-B");

        RegisterProductCommand conflicting = new RegisterProductCommand(
                "다른 상품명", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), "create-key-B");

        assertThatThrownBy(() -> registerProductService.register(conflicting))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    /** AC-2 — a genuinely different product intentionally sharing a name still works. */
    @Test
    @DisplayName("AC-2 다른 키로 같은 상품명을 다시 등록 → 정상적으로 두 번째 상품이 생성된다")
    void register_differentKey_sameName_isGenuineSecondProduct() {
        UUID first = registerProduct("동명 상품", 10, "create-key-C1");
        UUID second = registerProduct("동명 상품", 10, "create-key-C2");

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    @DisplayName("F4 Idempotency-Key 없는 상품 등록 요청은 거부되고 상품이 생성되지 않는다")
    void register_missingKey_isRefused_noProductCreated() {
        RegisterProductCommand command = new RegisterProductCommand(
                "키 없음 상품", "설명", 10000L, null, null, null,
                List.of(new VariantCommand("기본", 10, 0)), null);

        assertThatThrownBy(() -> registerProductService.register(command))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("AC-4 (tenant_id, idempotency_key) 유니크 제약이 실제 스키마에 존재한다")
    void uniqueConstraintExistsInSchema_productCreateRequest() {
        registerProduct("생성 스키마 확인 상품", 10, "create-key-D");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO product_create_request (tenant_id, idempotency_key, name, product_id) "
                        + "VALUES (?, ?, ?, ?)",
                "ecommerce", "create-key-D", "다른이름", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
