package com.example.product.infrastructure.reconciliation;

import com.example.product.ProductServiceApplication;
import com.example.product.domain.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authoritative DB gate for ADR-MONO-022 §D4 v2(b): validates the V12 migration
 * (variant `sku` + the three reconciliation tables) + JPA mappings + the trajectory
 * delta persisting to {@code product_variants.stock} on real Postgres. The Kafka hop is
 * covered by the consumer unit tests (listeners disabled here).
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@Transactional
@DisplayName("wms inventory reconciliation 통합 테스트 (DB delta)")
class WmsInventoryReconciliationIT {

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
        // wms consumers exist (@Profile !standalone) but we exercise the service directly.
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired private WmsInventoryReconciliationService service;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private WmsSkuSnapshotRepository skuSnapshotRepository;
    @Autowired private WmsInventoryAvailableRepository inventoryAvailableRepository;

    // V8 seed variant 'c0000000-...-0001' (stock 50); V12 backfills its sku deterministically.
    private static final UUID SEED_VARIANT = UUID.fromString("c0000000-0000-0000-0000-000000000001");
    private static final String SEED_SKU = "SKU-EC-000000000001";

    @Test
    @DisplayName("master.sku 스냅샷 + 두 inventory 관측 → 시드 variant 재고가 DB에서 delta만큼 차감")
    void reconciles_seedVariantStock_inDb() {
        UUID skuId = UUID.randomUUID();
        UUID inventoryId = UUID.randomUUID();

        // sanity: seed sku resolves to the seed variant
        assertThat(inventoryRepository.findVariantBySku(SEED_SKU))
                .as("V12 backfilled the seed variant sku").isPresent()
                .get().extracting(InventoryRepository.VariantRef::variantId).isEqualTo(SEED_VARIANT);
        int initialStock = inventoryRepository.findByVariantId(SEED_VARIANT).orElseThrow().currentStock().value();
        assertThat(initialStock).isEqualTo(50);

        // 1. reverse-identity snapshot
        service.upsertSkuSnapshot(skuId, SEED_SKU, 0);
        assertThat(skuSnapshotRepository.findById(skuId)).isPresent();

        // 2. baseline observation (delta 0 — no stock change)
        service.reconcileAvailable(inventoryId, skuId, 200);
        assertThat(inventoryRepository.findByVariantId(SEED_VARIANT).orElseThrow().currentStock().value())
                .as("baseline does not move stock").isEqualTo(50);
        assertThat(inventoryAvailableRepository.findById(inventoryId)).isPresent()
                .get().extracting(WmsInventoryAvailableEntity::getAvailableQty).isEqualTo(200);

        // 3. warehouse loses 5 → delta -5 applied to the sellable stock
        service.reconcileAvailable(inventoryId, skuId, 195);

        assertThat(inventoryRepository.findByVariantId(SEED_VARIANT).orElseThrow().currentStock().value())
                .as("delta -5 reduced the seed variant stock in the DB").isEqualTo(45);
        assertThat(inventoryAvailableRepository.findById(inventoryId).orElseThrow().getAvailableQty())
                .isEqualTo(195);
    }
}
