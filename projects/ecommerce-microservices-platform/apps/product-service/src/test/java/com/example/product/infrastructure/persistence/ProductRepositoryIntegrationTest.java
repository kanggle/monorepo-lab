package com.example.product.infrastructure.persistence;

import com.example.product.domain.exception.VariantNotFoundException;
import com.example.product.domain.model.*;
import com.example.product.domain.repository.CategoryRepository;
import com.example.product.domain.repository.InventoryRepository;
import com.example.product.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
@DisplayName("ProductRepository 통합 테스트")
class ProductRepositoryIntegrationTest {

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
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("상품을 저장하고 조회할 수 있다")
    void save_andFindById_success() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(10), new Price(0));
        Product product = Product.create("테스트 상품", "설명입니다", new Price(15000), null, List.of(variant));

        productRepository.save(product);

        em.flush();
        em.clear();

        Optional<Product> found = productRepository.findById(product.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("테스트 상품");
        assertThat(found.get().getDescription()).isEqualTo("설명입니다");
        assertThat(found.get().getPrice()).isEqualTo(new Price(15000));
        assertThat(found.get().getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("상품과 variant가 함께 저장된다")
    void save_withVariants_persistsVariants() {
        ProductVariant variant1 = ProductVariant.create("S", new StockQuantity(5), new Price(0));
        ProductVariant variant2 = ProductVariant.create("M", new StockQuantity(10), new Price(1000));
        Product product = Product.create("사이즈 상품", "설명", new Price(20000), null, List.of(variant1, variant2));

        productRepository.save(product);

        em.flush();
        em.clear();

        Optional<Product> found = productRepository.findById(product.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getVariants()).hasSize(2);
    }

    @Test
    @DisplayName("카테고리와 함께 상품을 저장할 수 있다")
    void save_withCategory_success() {
        Category category = Category.create("전자제품", null);
        categoryRepository.save(category);

        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(3), new Price(0));
        Product product = Product.create("노트북", "고성능 노트북", new Price(1500000), category.getId(), List.of(variant));

        productRepository.save(product);

        em.flush();
        em.clear();

        Optional<Product> found = productRepository.findById(product.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getCategoryId()).isEqualTo(category.getId());
    }

    @Test
    @DisplayName("존재하지 않는 ID로 조회하면 빈 결과를 반환한다")
    void findById_notFound_returnsEmpty() {
        Optional<Product> found = productRepository.findById(UUID.randomUUID());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("상품 존재 여부를 확인할 수 있다")
    void existsById_existingProduct_returnsTrue() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(1), new Price(0));
        Product product = Product.create("존재 확인 상품", "설명", new Price(5000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        assertThat(productRepository.existsById(product.getId())).isTrue();
        assertThat(productRepository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("상품 이름 변경이 DB에 반영된다")
    void save_update_nameChange_persists() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(5), new Price(0));
        Product product = Product.create("원래 이름", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        Product loaded = productRepository.findById(product.getId()).orElseThrow();
        loaded.updateName("변경된 이름");
        productRepository.save(loaded);

        em.flush();
        em.clear();

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("변경된 이름");
    }

    @Test
    @DisplayName("variant 추가 후 save하면 DB에 반영된다")
    void save_update_addVariant_persists() {
        ProductVariant variant = ProductVariant.create("S", new StockQuantity(5), new Price(0));
        Product product = Product.create("사이즈 상품", "설명", new Price(20000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        Product loaded = productRepository.findById(product.getId()).orElseThrow();
        loaded.addVariant(ProductVariant.create("L", new StockQuantity(3), new Price(2000)));
        productRepository.save(loaded);

        em.flush();
        em.clear();

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getVariants()).hasSize(2);
    }

    @Test
    @DisplayName("variant의 재고를 조회할 수 있다")
    void inventoryRepository_findByVariantId_returnsStock() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(20), new Price(0));
        Product product = Product.create("재고 테스트 상품", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        Optional<Inventory> found = inventoryRepository.findByVariantId(variant.getId());
        assertThat(found).isPresent();
        assertThat(found.get().currentStock()).isEqualTo(new StockQuantity(20));
    }

    @Test
    @DisplayName("재고를 감소시키면 DB에 반영된다")
    void inventoryRepository_save_decreaseStock_persists() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(10), new Price(0));
        Product product = Product.create("재고 수정 상품", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        Inventory inventory = inventoryRepository.findByVariantId(variant.getId()).orElseThrow();
        inventory.decrease(3);
        inventoryRepository.save(inventory);

        em.flush();
        em.clear();

        Optional<Inventory> updated = inventoryRepository.findByVariantId(variant.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().currentStock()).isEqualTo(new StockQuantity(7));
    }

    @Test
    @DisplayName("soft-delete된 상품은 findById로 조회되지 않는다")
    void findById_softDeleted_returnsEmpty() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(5), new Price(0));
        Product product = Product.create("삭제될 상품", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        productRepository.softDelete(product.getId());

        em.flush();
        em.clear();

        Optional<Product> found = productRepository.findById(product.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("soft-delete된 상품은 existsById에서도 false를 반환한다")
    void existsById_softDeleted_returnsFalse() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(5), new Price(0));
        Product product = Product.create("삭제될 상품", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        productRepository.softDelete(product.getId());

        em.flush();
        em.clear();

        assertThat(productRepository.existsById(product.getId())).isFalse();
    }

    @Test
    @DisplayName("soft-delete된 상품에 대해 findById와 existsById가 일관된 결과를 반환한다")
    void findById_existsById_consistency_afterSoftDelete() {
        ProductVariant variant = ProductVariant.create("기본", new StockQuantity(5), new Price(0));
        Product product = Product.create("일관성 테스트 상품", "설명", new Price(10000), null, List.of(variant));
        productRepository.save(product);

        em.flush();
        em.clear();

        // soft-delete 전: 둘 다 존재
        assertThat(productRepository.findById(product.getId())).isPresent();
        assertThat(productRepository.existsById(product.getId())).isTrue();

        productRepository.softDelete(product.getId());

        em.flush();
        em.clear();

        // soft-delete 후: 둘 다 미존재
        assertThat(productRepository.findById(product.getId())).isEmpty();
        assertThat(productRepository.existsById(product.getId())).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 variant ID로 재고 저장 시 예외가 발생한다")
    void inventoryRepository_save_nonExistentVariant_throws() {
        UUID nonExistentId = UUID.randomUUID();
        Inventory inventory = Inventory.create(nonExistentId, new StockQuantity(5));

        assertThatThrownBy(() -> inventoryRepository.save(inventory))
                .isInstanceOf(VariantNotFoundException.class)
                .hasMessageContaining(nonExistentId.toString());
    }
}
