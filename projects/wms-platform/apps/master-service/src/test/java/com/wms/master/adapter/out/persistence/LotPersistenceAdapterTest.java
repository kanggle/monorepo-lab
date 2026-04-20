package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
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
 * Testcontainers-backed persistence adapter test for Lot. Uses Postgres 16 alpine
 * to verify constraints that cannot be expressed in JPA or H2:
 * <ul>
 *   <li>Unique constraint {@code uq_lots_sku_lotno} detection via
 *       {@code PSQLException.getServerErrorMessage().getConstraint()} path (BE-009 pattern)
 *   <li>Raw-SQL {@code ck_lots_date_pair} CHECK constraint bypass
 *   <li>Partial index {@code idx_lots_expiry_active} correctness
 *   <li>Cross-SKU same lotNo allowed (per-SKU uniqueness)
 * </ul>
 *
 * <p>Annotated {@code @Testcontainers(disabledWithoutDocker = true)} — on Windows
 * native environments where Docker Desktop is unavailable the entire class is skipped
 * automatically; no explicit {@code @Disabled} or conditional needed.
 *
 * <p>The H2 counterpart {@link LotPersistenceAdapterH2Test} covers remaining ORM
 * paths without requiring Docker.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        LotPersistenceMapper.class,
        LotPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class LotPersistenceAdapterTest {

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
    LotPersistencePort adapter;

    @Autowired
    JpaLotRepository jpaRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @DisplayName("insert persists a new Lot and assigns version 0")
    void insertPersistsNew() {
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        Lot created = lot(skuId, "LOT-TC-001", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        Lot saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert path mapper: version=null produces a version-0 row (defence against detached-persist confusion)")
    void insertPathMapper_versionNullProducesVersionZero() {
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        Lot created = lot(skuId, "LOT-TC-VER", null, null);

        Lot saved = adapter.insert(created);

        assertThat(saved.getVersion()).isZero();
        jpaRepository.findById(created.getId()).ifPresent(entity ->
                assertThat(entity.getVersion()).isZero());
    }

    @Test
    @DisplayName("uq_lots_sku_lotno constraint: duplicate (same skuId + lotNo) raises LotNoDuplicateException via constraint-name path")
    void duplicateLotNo_raisesLotNoDuplicateExceptionViaConstraintName() {
        // The adapter detects the Postgres constraint name "uq_lots_sku_lotno" from
        // PSQLException.getServerErrorMessage().getConstraint() and raises the typed
        // domain exception — not a generic DataIntegrityViolationException.
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        Lot first = lot(skuId, "LOT-DUP", null, null);
        Lot second = lot(skuId, "LOT-DUP", null, null);

        adapter.insert(first);

        assertThatThrownBy(() -> adapter.insert(second))
                .isInstanceOf(LotNoDuplicateException.class);
    }

    @Test
    @DisplayName("raw-SQL ck_lots_date_pair: inserting expiry < manufactured trips the CHECK constraint")
    void rawSqlInsert_badDatePair_tripsCheckConstraint() {
        // The domain factory validates the date pair, so this path cannot be reached
        // via the normal adapter API. Native SQL bypasses domain validation to confirm
        // the DB-level CHECK fires independently (defense-in-depth).
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        UUID id = UUID.randomUUID();

        String insertSql = """
                INSERT INTO lots (
                    id, sku_id, lot_no, manufactured_date, expiry_date,
                    status, version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    CAST(:id AS uuid), CAST(:skuId AS uuid), :lotNo,
                    DATE '2026-05-01', DATE '2026-04-01',
                    'ACTIVE', 0, NOW(), 'test', NOW(), 'test'
                )
                """;

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(insertSql)
                    .setParameter("id", id.toString())
                    .setParameter("skuId", skuId.toString())
                    .setParameter("lotNo", "LOT-BAD-DATES")
                    .executeUpdate();
            entityManager.flush();
        }).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    @DisplayName("idx_lots_expiry_active partial index correctness: only ACTIVE lots before cutoff are returned")
    void partialIndex_expiry_active_correctness() {
        // Seed 3 lots:
        // 1. ACTIVE + expired yesterday → should be returned by the scheduler query
        // 2. ACTIVE + expires tomorrow   → must NOT be returned
        // 3. EXPIRED + expired yesterday → must NOT be returned (status != ACTIVE)
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        LocalDate today = LocalDate.of(2026, 4, 20);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        Lot activeExpiredYesterday = adapter.insert(
                lot(skuId, "LOT-PI-ACTIVE-PAST", null, yesterday));
        Lot activeExpiresTomorrow = adapter.insert(
                lot(skuId, "LOT-PI-ACTIVE-FUTURE", null, tomorrow));

        // Expire the third lot by putting it in EXPIRED state via domain transition
        Lot toExpire = adapter.insert(lot(skuId, "LOT-PI-EXPIRED-PAST", null, yesterday));
        Lot loadedToExpire = adapter.findById(toExpire.getId()).orElseThrow();
        loadedToExpire.expire(ACTOR);
        adapter.update(loadedToExpire);

        // The scheduler query: ACTIVE lots with expiry_date < today
        List<Lot> candidates = adapter.findAllByStatusAndExpiryDateBefore(LotStatus.ACTIVE, today);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getId()).isEqualTo(activeExpiredYesterday.getId());
    }

    @Test
    @DisplayName("cross-SKU same lotNo is allowed: two SKUs can share the same lotNo string")
    void crossSkuSameLotNoIsAllowed() {
        UUID skuId1 = UUID.randomUUID();
        UUID skuId2 = UUID.randomUUID();
        seedSku(skuId1);
        seedSku(skuId2);

        Lot first = adapter.insert(lot(skuId1, "SHARED-LOT", null, null));
        Lot second = adapter.insert(lot(skuId2, "SHARED-LOT", null, null));

        assertThat(jpaRepository.findById(first.getId())).isPresent();
        assertThat(jpaRepository.findById(second.getId())).isPresent();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        UUID skuId = UUID.randomUUID();
        seedSku(skuId);
        Lot s = lot(skuId, "LOT-TC-OPT", null, null);
        adapter.insert(s);

        Lot firstLoad = adapter.findById(s.getId()).orElseThrow();
        Lot secondLoad = adapter.findById(s.getId()).orElseThrow();

        firstLoad.applyUpdate(LocalDate.of(2027, 1, 1), null, false, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate(LocalDate.of(2028, 1, 1), null, false, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // ---------- helpers ----------

    private static Lot lot(UUID skuId, String lotNo, LocalDate mfd, LocalDate expiry) {
        return Lot.create(skuId, lotNo, mfd, expiry, null, ACTOR);
    }

    /**
     * Seeds a parent SKU row with {@code tracking_type=LOT, status=ACTIVE} via
     * native SQL so Lot insert tests can satisfy the
     * {@code sku_id UUID NOT NULL REFERENCES skus(id)} foreign key without
     * going through the SKU domain factory. Tests that need multiple distinct
     * parent SKUs call this once per UUID before inserting Lots.
     *
     * <p>Uses native SQL (bypasses domain/Hibernate) so test setup stays
     * orthogonal to SKU adapter behaviour — these tests verify Lot constraints,
     * not SKU ones.
     */
    private void seedSku(UUID skuId) {
        entityManager.createNativeQuery("""
                INSERT INTO skus (
                    id, sku_code, name, base_uom, tracking_type, status,
                    version, created_at, created_by, updated_at, updated_by
                ) VALUES (
                    CAST(:id AS uuid), :skuCode, :name, 'EA', 'LOT', 'ACTIVE',
                    0, NOW(), 'test', NOW(), 'test'
                )
                """)
                .setParameter("id", skuId.toString())
                .setParameter("skuCode", "SKU-TC-" + skuId.toString().substring(0, 8).toUpperCase())
                .setParameter("name", "Test SKU " + skuId)
                .executeUpdate();
        entityManager.flush();
    }
}
