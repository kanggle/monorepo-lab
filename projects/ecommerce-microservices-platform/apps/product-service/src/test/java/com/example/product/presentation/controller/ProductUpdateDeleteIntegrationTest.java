package com.example.product.presentation.controller;

import com.example.product.application.command.RegisterProductCommand;
import com.example.product.application.command.UpdateProductCommand;
import com.example.product.application.command.VariantCommand;
import com.example.product.application.service.DeleteProductService;
import com.example.product.application.service.QueryProductService;
import com.example.product.application.service.RegisterProductService;
import com.example.product.application.service.UpdateProductService;
import com.example.product.domain.event.ProductDeletedPayload;
import com.example.product.domain.event.ProductEvent;
import com.example.product.domain.event.ProductUpdatedPayload;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Tag("integration")
@Testcontainers
@Transactional
@RecordApplicationEvents
@DisplayName("상품 수정 + 삭제 통합 테스트")
class ProductUpdateDeleteIntegrationTest {

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
    private UpdateProductService updateProductService;

    @Autowired
    private DeleteProductService deleteProductService;

    @Autowired
    private QueryProductService queryProductService;

    @Autowired
    private ApplicationEvents applicationEvents;

    private UUID registerProduct(String name) {
        return registerProductService.register(new RegisterProductCommand(
                name, "설명", 10000L, null,
                List.of(new VariantCommand("기본", 10, 0))));
    }

    @Test
    @DisplayName("상품 수정 후 상세 조회 시 변경된 값이 반환된다")
    void update_thenDetailReflectsChanges() {
        UUID productId = registerProduct("원래 이름");

        updateProductService.update(new UpdateProductCommand(productId, "수정된 이름", null, 25000L, null));

        var detail = queryProductService.findById(productId);
        assertThat(detail.name()).isEqualTo("수정된 이름");
        assertThat(detail.price()).isEqualTo(25000L);
    }

    @Test
    @DisplayName("수정 후 ProductUpdated 이벤트가 발행된다")
    void update_publishesProductUpdatedEvent() {
        UUID productId = registerProduct("이벤트 상품");

        updateProductService.update(new UpdateProductCommand(productId, "이벤트 수정 상품", null, null, ProductStatus.HIDDEN));

        List<ProductEvent> events = applicationEvents.stream(ProductEvent.class)
                .filter(e -> e.eventType().equals("ProductUpdated"))
                .toList();
        assertThat(events).hasSize(1);

        ProductUpdatedPayload payload = (ProductUpdatedPayload) events.get(0).payload();
        assertThat(payload.productId()).isEqualTo(productId.toString());
        assertThat(payload.name()).isEqualTo("이벤트 수정 상품");
        assertThat(payload.status()).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("상품 삭제 후 조회 시 404 반환")
    void delete_thenFindByIdThrowsNotFound() {
        UUID productId = registerProduct("삭제될 상품");

        deleteProductService.delete(productId);

        assertThatThrownBy(() -> queryProductService.findById(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("삭제 후 ProductDeleted 이벤트가 발행된다")
    void delete_publishesProductDeletedEvent() {
        UUID productId = registerProduct("삭제 이벤트 상품");

        deleteProductService.delete(productId);

        List<ProductEvent> events = applicationEvents.stream(ProductEvent.class)
                .filter(e -> e.eventType().equals("ProductDeleted"))
                .toList();
        assertThat(events).hasSize(1);

        ProductDeletedPayload payload = (ProductDeletedPayload) events.get(0).payload();
        assertThat(payload.productId()).isEqualTo(productId.toString());
    }

    @Test
    @DisplayName("이미 삭제된 상품 다시 삭제 시 404 반환")
    void delete_alreadyDeleted_throwsNotFound() {
        UUID productId = registerProduct("중복 삭제 상품");
        deleteProductService.delete(productId);

        assertThatThrownBy(() -> deleteProductService.delete(productId))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("삭제된 상품은 목록 조회에 포함되지 않는다")
    void delete_thenNotInList() {
        UUID productId = registerProduct("목록에서 제거될 상품");

        deleteProductService.delete(productId);

        var result = queryProductService.findAll(null, null, 0, 20);
        assertThat(result.content().stream().noneMatch(p -> p.id().equals(productId))).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 상품 수정 시 ProductNotFoundException 발생")
    void update_notFound_throwsException() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> updateProductService.update(
                new UpdateProductCommand(unknownId, "이름", null, null, null)))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
