package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

/**
 * H2-backed persistence adapter test for Lot. Exercises ORM paths without
 * requiring Docker so the JPA / exception-translation path is covered on every
 * CI run.
 *
 * <p>What this test does NOT cover (Testcontainers variant does):
 * <ul>
 *   <li>Partial unique index {@code uq_lots_sku_lotno} semantics via
 *       {@code PSQLException.getServerErrorMessage().getConstraint()} —
 *       H2 raises a different exception structure, so constraint-name
 *       extraction falls back to substring match.
 *   <li>{@code CHECK (ck_lots_date_pair)} DB-level guard — H2's JPA DDL
 *       auto-creation does not emit the same CHECK constraint. See
 *       {@link LotPersistenceAdapterTest} (Testcontainers) for that coverage.
 *   <li>Partial index {@code idx_lots_expiry_active} correctness — partial
 *       index filter syntax is Postgres-specific. See Testcontainers variant.
 * </ul>
 */
@DataJpaTest
@Import({MasterServicePersistenceConfig.class,
        LotPersistenceMapper.class,
        LotPersistenceAdapter.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:lot_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class LotPersistenceAdapterH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    LotPersistencePort adapter;

    @Autowired
    JpaLotRepository jpaRepository;

    @Test
    @DisplayName("insert persists a new Lot and assigns version 0")
    void insertPersistsNew() {
        UUID skuId = UUID.randomUUID();
        Lot created = lot(skuId, "LOT-H2-001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        Lot saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert path mapper: version=null produces a version-0 row")
    void insertPath_versionNullProducesVersionZero() {
        UUID skuId = UUID.randomUUID();
        Lot created = lot(skuId, "LOT-H2-VER", null, LocalDate.of(2027, 1, 1));

        Lot saved = adapter.insert(created);

        assertThat(saved.getVersion()).isZero();
        jpaRepository.findById(created.getId()).ifPresent(entity ->
                assertThat(entity.getVersion()).isZero());
    }

    @Test
    @DisplayName("findById returns empty for unknown id")
    void findByIdMiss() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findById round-trip returns the persisted Lot")
    void findByIdRoundTrip() {
        UUID skuId = UUID.randomUUID();
        Lot created = adapter.insert(lot(skuId, "LOT-H2-RT", null, LocalDate.of(2027, 6, 1)));

        Optional<Lot> found = adapter.findById(created.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getLotNo()).isEqualTo("LOT-H2-RT");
        assertThat(found.get().getSkuId()).isEqualTo(skuId);
    }

    @Test
    @DisplayName("update: mutable fields change; version bumps; immutable fields preserved")
    void updateVersionBumps() {
        UUID skuId = UUID.randomUUID();
        Lot inserted = adapter.insert(lot(skuId, "LOT-H2-UPD",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        assertThat(inserted.getVersion()).isZero();

        Lot loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate(LocalDate.of(2027, 6, 30), null, false, ACTOR);
        adapter.update(loaded);

        Lot reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getExpiryDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        // immutable fields preserved
        assertThat(reloaded.getLotNo()).isEqualTo("LOT-H2-UPD");
        assertThat(reloaded.getSkuId()).isEqualTo(skuId);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("insert duplicate (same skuId + lotNo) raises LotNoDuplicateException")
    void insertDuplicateLotNo() {
        UUID skuId = UUID.randomUUID();
        adapter.insert(lot(skuId, "LOT-DUP", null, null));

        Lot duplicate = lot(skuId, "LOT-DUP", null, null);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(LotNoDuplicateException.class);
    }

    @Test
    @DisplayName("same lotNo across different SKUs is allowed (per-SKU uniqueness)")
    void sameLotNoDifferentSkuIsAllowed() {
        UUID skuId1 = UUID.randomUUID();
        UUID skuId2 = UUID.randomUUID();

        adapter.insert(lot(skuId1, "LOT-SHARED", null, null));
        Lot second = adapter.insert(lot(skuId2, "LOT-SHARED", null, null));

        assertThat(second.getId()).isNotNull();
        assertThat(jpaRepository.findById(second.getId())).isPresent();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        UUID skuId = UUID.randomUUID();
        Lot inserted = adapter.insert(lot(skuId, "LOT-OPT", null, null));

        Lot first = adapter.findById(inserted.getId()).orElseThrow();
        Lot second = adapter.findById(inserted.getId()).orElseThrow();

        first.applyUpdate(LocalDate.of(2027, 1, 1), null, false, "actor-first");
        adapter.update(first);

        second.applyUpdate(LocalDate.of(2027, 6, 1), null, false, "actor-second");
        assertThatThrownBy(() -> adapter.update(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage filters by skuId and status, paginates correctly")
    void findPage_filtersBySkuIdAndStatus() {
        UUID skuId1 = UUID.randomUUID();
        UUID skuId2 = UUID.randomUUID();
        Lot lot1 = adapter.insert(lot(skuId1, "LOT-PAGE-A", null, LocalDate.of(2027, 1, 1)));
        Lot lot2 = adapter.insert(lot(skuId1, "LOT-PAGE-B", null, null));
        Lot lot3 = adapter.insert(lot(skuId2, "LOT-PAGE-C", null, null));

        // Deactivate lot2
        Lot loaded2 = adapter.findById(lot2.getId()).orElseThrow();
        loaded2.deactivate(ACTOR);
        adapter.update(loaded2);

        // Filter by skuId1 + ACTIVE
        PageResult<Lot> result = adapter.findPage(
                new ListLotsCriteria(skuId1, LotStatus.ACTIVE, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).getLotNo()).isEqualTo("LOT-PAGE-A");

        // Filter by skuId1 only (both active and inactive)
        PageResult<Lot> allSku1 = adapter.findPage(
                new ListLotsCriteria(skuId1, null, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(allSku1.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findPage filters by expiryBefore")
    void findPage_filtersByExpiryBefore() {
        UUID skuId = UUID.randomUUID();
        LocalDate jan = LocalDate.of(2026, 1, 31);
        LocalDate dec = LocalDate.of(2026, 12, 31);
        adapter.insert(lot(skuId, "LOT-EXP-JAN", null, jan));
        adapter.insert(lot(skuId, "LOT-EXP-DEC", null, dec));
        adapter.insert(lot(skuId, "LOT-NO-EXP",  null, null));

        // Only the January lot expires before 2026-06-01
        PageResult<Lot> result = adapter.findPage(
                new ListLotsCriteria(skuId, null, LocalDate.of(2026, 6, 1), null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content().get(0).getLotNo()).isEqualTo("LOT-EXP-JAN");
    }

    @Test
    @DisplayName("findAllByStatusAndExpiryDateBefore returns only ACTIVE lots before cutoff")
    void findAllByStatusAndExpiryDateBefore() {
        UUID skuId = UUID.randomUUID();
        Lot expirable = adapter.insert(lot(skuId, "LOT-SCHED-1",
                null, LocalDate.of(2026, 3, 1)));
        Lot today = adapter.insert(lot(skuId, "LOT-SCHED-2",
                null, LocalDate.of(2026, 4, 20)));
        Lot future = adapter.insert(lot(skuId, "LOT-SCHED-3",
                null, LocalDate.of(2027, 1, 1)));
        Lot noExpiry = adapter.insert(lot(skuId, "LOT-SCHED-4", null, null));

        List<Lot> found = adapter.findAllByStatusAndExpiryDateBefore(
                LotStatus.ACTIVE, LocalDate.of(2026, 4, 20));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(expirable.getId());
    }

    // ---------- helpers ----------

    private static Lot lot(UUID skuId, String lotNo, LocalDate mfd, LocalDate expiry) {
        return Lot.create(skuId, lotNo, mfd, expiry, null, ACTOR);
    }
}
