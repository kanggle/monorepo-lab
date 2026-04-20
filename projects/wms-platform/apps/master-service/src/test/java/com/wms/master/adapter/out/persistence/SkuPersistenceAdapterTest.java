package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Testcontainers-backed persistence adapter test for SKU. Uses Postgres 16
 * alpine to verify constraints that cannot be expressed in JPA or H2:
 * <ul>
 *   <li>Partial unique index {@code uq_skus_barcode} (WHERE barcode IS NOT NULL)
 *   <li>{@code CHECK (sku_code = UPPER(sku_code))} defence-in-depth guard
 *   <li>{@link BarcodeDuplicateException} translation via
 *       {@code PSQLException.getServerErrorMessage().getConstraint()}
 * </ul>
 *
 * <p>The H2 counterpart {@link SkuPersistenceAdapterH2Test} covers the remaining
 * ORM cases without requiring Docker.
 *
 * <p>Annotated {@code @Testcontainers(disabledWithoutDocker = true)} — on Windows
 * CI environments where Docker Desktop is unavailable the entire class is skipped
 * automatically; no explicit {@code @Disabled} or conditional needed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        SkuPersistenceMapper.class,
        SkuPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class SkuPersistenceAdapterTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("master_test")
            .withUsername("master_test")
            .withPassword("master_test");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    private static final String ACTOR = "actor-uuid";

    @Autowired
    SkuPersistencePort adapter;

    @Autowired
    JpaSkuRepository jpaRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("insert persists a new SKU and assigns version 0")
    void insertPersistsNew() {
        Sku created = sku("SKU-TC-001", "Gala Apple 1kg", null,
                BaseUom.EA, TrackingType.LOT, 30);

        Sku saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert path mapper: version=null produces a version-0 row (defence against detached-persist confusion)")
    void insertPathMapper_versionNullProducesVersionZero() {
        // This test specifically exercises the toInsertEntity() path which emits
        // version=null so Spring Data JPA treats the entity as new and runs INSERT.
        // After the flush the persisted row carries the DB DEFAULT 0.
        Sku created = sku("SKU-TC-VER", "Version zero test", null,
                BaseUom.BOX, TrackingType.NONE, null);

        Sku saved = adapter.insert(created);

        assertThat(saved.getVersion()).isZero();
        // Confirm the JPA entity on disk also has version 0
        jpaRepository.findById(created.getId()).ifPresent(entity ->
                assertThat(entity.getVersion()).isZero());
    }

    @Test
    @DisplayName("two SKUs with NULL barcode coexist — partial unique index allows multiple NULLs")
    void multipleNullBarcodesCoexist() {
        // Partial index: WHERE barcode IS NOT NULL — NULLs are not covered,
        // so two separate SKUs without a barcode must not conflict.
        Sku first = sku("SKU-TC-N01", "No Barcode First",  null, BaseUom.EA, TrackingType.NONE, null);
        Sku second = sku("SKU-TC-N02", "No Barcode Second", null, BaseUom.EA, TrackingType.NONE, null);

        assertThat(adapter.insert(first).getId()).isNotNull();
        assertThat(adapter.insert(second).getId()).isNotNull();
        // Both rows exist
        assertThat(jpaRepository.findById(first.getId())).isPresent();
        assertThat(jpaRepository.findById(second.getId())).isPresent();
    }

    @Test
    @DisplayName("two SKUs with the same non-null barcode → BarcodeDuplicateException via constraint-name path")
    void duplicateNonNullBarcode_raisesBarcodeDuplicateException() {
        // Both domain objects share the same barcode string. The adapter's
        // translateIntegrityViolation() must detect the Postgres constraint name
        // "uq_skus_barcode" from PSQLException.getServerErrorMessage().getConstraint()
        // and raise the typed domain exception.
        String sharedBarcode = "8801234567890";
        Sku first = sku("SKU-TC-B01", "First with barcode",  sharedBarcode, BaseUom.EA, TrackingType.NONE, null);
        Sku second = sku("SKU-TC-B02", "Second with barcode", sharedBarcode, BaseUom.EA, TrackingType.NONE, null);

        adapter.insert(first);

        assertThatThrownBy(() -> adapter.insert(second))
                .isInstanceOf(BarcodeDuplicateException.class)
                .hasMessageContaining(sharedBarcode);
    }

    @Test
    @DisplayName("raw-SQL insert with mixed-case sku_code trips the CHECK (sku_code = UPPER(sku_code)) constraint")
    void rawSqlInsert_mixedCaseSkuCode_tripsCheckConstraint() {
        // The domain factory always uppercases skuCode, so this bypass path cannot
        // be triggered through the normal adapter API. We use native SQL to prove
        // that the defence-in-depth DB constraint fires even when the application
        // layer is bypassed (e.g. a direct DB write or a migration bug).
        UUID id = UUID.randomUUID();
        String insertSql = """
                INSERT INTO skus (
                    id, sku_code, name, base_uom, tracking_type, status,
                    version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    CAST(:id AS uuid), :skuCode, :name, 'EA', 'NONE', 'ACTIVE',
                    0, NOW(), 'test', NOW(), 'test'
                )
                """;

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(insertSql)
                    .setParameter("id", id.toString())
                    .setParameter("skuCode", "sku-lowercase")    // violates CHECK (sku_code = UPPER(sku_code))
                    .setParameter("name", "Should fail")
                    .executeUpdate();
            entityManager.flush();
        }).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Sku s = sku("SKU-TC-OPT", "Concurrent", null, BaseUom.EA, TrackingType.NONE, null);
        adapter.insert(s);

        // Two separate loads represent two concurrent actors reading the same version
        Sku firstLoad  = adapter.findById(s.getId()).orElseThrow();
        Sku secondLoad = adapter.findById(s.getId()).orElseThrow();

        firstLoad.applyUpdate("Changed by first", null, null, null, null, null, null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Changed by second (stale)", null, null, null, null, null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ---------- helpers ----------

    private static Sku sku(String code, String name, String barcode,
                           BaseUom uom, TrackingType tracking, Integer shelfLifeDays) {
        return Sku.create(code, name, null, barcode, uom, tracking,
                null, null, null, shelfLifeDays, ACTOR);
    }
}
