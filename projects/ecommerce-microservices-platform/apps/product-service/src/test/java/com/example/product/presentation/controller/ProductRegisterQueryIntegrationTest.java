package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.domain.event.ProductCreatedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.model.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@Transactional
@DisplayName("상품 등록 + 조회 통합 테스트")
class ProductRegisterQueryIntegrationTest {

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
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RegisterProductService registerProductService;

    @Autowired
    private QueryProductService queryProductService;

    /**
     * The {@code ProductCreated} event published (via the mocked Kafka transport)
     * for {@code productId}. Events accumulate on the context-shared mock across
     * methods, so filter by the product just registered.
     */
    private ProductEvent publishedCreatedEvent(UUID productId) {
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, atLeastOnce())
                .send(eq("product.product.created"), anyString(), captor.capture());
        return captor.getAllValues().stream()
                .map(ProductEvent.class::cast)
                .filter(e -> e.payload() instanceof ProductCreatedPayload p
                        && p.productId().equals(productId.toString()))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    @DisplayName("상품 등록 후 목록 조회에 포함된다")
    void register_thenListContainsProduct() {
        RegisterProductCommand command = new RegisterProductCommand(
                "테스트 상품", "설명", 15000L, null,
                List.of(new VariantCommand("기본", 10, 0)));

        UUID id = registerProductService.register(command);

        var result = queryProductService.findAll(null, null, 0, 20);
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content().stream().anyMatch(p -> p.id().equals(id))).isTrue();
    }

    @Test
    @DisplayName("상품 등록 후 상세 조회 시 variants 포함 반환")
    void register_thenDetailContainsVariants() {
        RegisterProductCommand command = new RegisterProductCommand(
                "사이즈 상품", "사이즈 있는 상품", 20000L, null,
                List.of(
                        new VariantCommand("S", 5, 0),
                        new VariantCommand("M", 10, 1000)));

        UUID id = registerProductService.register(command);

        var detail = queryProductService.findById(id);
        assertThat(detail.id()).isEqualTo(id);
        assertThat(detail.name()).isEqualTo("사이즈 상품");
        assertThat(detail.variants()).hasSize(2);
    }

    @Test
    @DisplayName("등록 성공 시 ProductCreated 이벤트가 발행된다")
    void register_publishesProductCreatedEvent() {
        RegisterProductCommand command = new RegisterProductCommand(
                "이벤트 상품", "이벤트 테스트", 12000L, null,
                List.of(new VariantCommand("기본", 5, 0)));

        UUID id = registerProductService.register(command);

        ProductEvent event = publishedCreatedEvent(id);
        assertThat(event.eventType()).isEqualTo("ProductCreated");
        assertThat(event.source()).isEqualTo("product-service");
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();

        assertThat(event.payload()).isInstanceOf(ProductCreatedPayload.class);
        ProductCreatedPayload payload = (ProductCreatedPayload) event.payload();
        assertThat(payload.productId()).isEqualTo(id.toString());
        assertThat(payload.name()).isEqualTo("이벤트 상품");
        assertThat(payload.status()).isEqualTo("ON_SALE");
        assertThat(payload.variants()).hasSize(1);
        assertThat(payload.variants().get(0).optionName()).isEqualTo("기본");
    }

    @Test
    @DisplayName("이벤트 페이로드가 계약과 일치한다 (variantId, stock, additionalPrice 포함)")
    void register_eventPayloadMatchesContract() {
        RegisterProductCommand command = new RegisterProductCommand(
                "계약 검증 상품", "설명", 30000L, null,
                List.of(
                        new VariantCommand("S", 10, 0),
                        new VariantCommand("L", 5, 2000)));

        UUID id = registerProductService.register(command);

        ProductCreatedPayload payload = (ProductCreatedPayload) publishedCreatedEvent(id).payload();

        assertThat(payload.price()).isEqualTo(30000L);
        assertThat(payload.categoryId()).isNull();
        assertThat(payload.variants()).hasSize(2);
        payload.variants().forEach(v -> {
            assertThat(v.variantId()).isNotNull();
            assertThat(v.optionName()).isNotBlank();
            assertThat(v.stock()).isGreaterThanOrEqualTo(0);
            assertThat(v.additionalPrice()).isGreaterThanOrEqualTo(0L);
        });
    }

    @Test
    @DisplayName("status 필터로 목록 조회")
    void register_thenFilterByStatus_returnsMatchingProducts() {
        RegisterProductCommand command = new RegisterProductCommand(
                "판매 상품", "설명", 10000L, null,
                List.of(new VariantCommand("기본", 3, 0)));
        registerProductService.register(command);

        var result = queryProductService.findAll(null, ProductStatus.ON_SALE, 0, 20);
        assertThat(result.content()).isNotEmpty();
        assertThat(result.content()).allMatch(p -> p.status() == ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("존재하지 않는 ID 상세 조회 시 예외 발생")
    void findById_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> queryProductService.findById(unknownId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 categoryId 등록 시 InvalidCategoryException 발생")
    void register_invalidCategoryId_throwsInvalidCategoryException() {
        UUID fakeCategoryId = UUID.randomUUID();
        RegisterProductCommand command = new RegisterProductCommand(
                "상품", "설명", 10000L, fakeCategoryId,
                List.of(new VariantCommand("기본", 1, 0)));

        assertThatThrownBy(() -> registerProductService.register(command))
                .isInstanceOf(InvalidCategoryException.class);
    }

    @Test
    @DisplayName("페이지네이션이 정상 동작한다")
    void findAll_pagination_works() {
        for (int i = 0; i < 3; i++) {
            registerProductService.register(new RegisterProductCommand(
                    "상품" + i, "설명", 10000L, null,
                    List.of(new VariantCommand("기본", 1, 0))));
        }

        var page0 = queryProductService.findAll(null, null, 0, 2);
        var page1 = queryProductService.findAll(null, null, 1, 2);

        assertThat(page0.content()).hasSize(2);
        assertThat(page0.size()).isEqualTo(2);
        assertThat(page1.content().size()).isLessThanOrEqualTo(2);
        assertThat(page0.totalElements()).isGreaterThanOrEqualTo(3);
    }
}
