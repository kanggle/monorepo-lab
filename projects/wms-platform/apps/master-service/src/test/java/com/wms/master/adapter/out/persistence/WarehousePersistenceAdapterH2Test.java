package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import java.time.Instant;
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
 * H2-backed persistence adapter test — identical coverage to
 * {@link WarehousePersistenceAdapterTest} but without Testcontainers, so the
 * JPA/ORM path is exercised on every CI run regardless of Docker availability.
 * The Testcontainers version remains authoritative for the Flyway migration
 * against real Postgres.
 */
@DataJpaTest
@Import({MasterServicePersistenceConfig.class,
        WarehousePersistenceMapper.class,
        WarehousePersistenceAdapter.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:master_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class WarehousePersistenceAdapterH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    WarehousePersistencePort adapter;

    @Autowired
    JpaWarehouseRepository jpaRepository;

    @Autowired
    JpaZoneRepository jpaZoneRepository;

    @Test
    @DisplayName("insert persists a new warehouse and assigns version 0")
    void insertPersistsNew() {
        Warehouse created = Warehouse.create("WH01", "Seoul Main", "Seoul, Korea", "Asia/Seoul", ACTOR);

        Warehouse saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("insert translates duplicate warehouseCode to WarehouseCodeDuplicateException")
    void insertDuplicateCode() {
        adapter.insert(Warehouse.create("WH02", "First", null, "UTC", ACTOR));

        Warehouse duplicate = Warehouse.create("WH02", "Second", null, "UTC", ACTOR);

        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(WarehouseCodeDuplicateException.class)
                .hasMessageContaining("WH02");
    }

    @Test
    @DisplayName("findById returns the persisted warehouse")
    void findByIdHit() {
        Warehouse w = Warehouse.create("WH03", "Name", "Addr", "UTC", ACTOR);
        adapter.insert(w);

        Optional<Warehouse> found = adapter.findById(w.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getWarehouseCode()).isEqualTo("WH03");
        assertThat(found.get().getName()).isEqualTo("Name");
        assertThat(found.get().getAddress()).isEqualTo("Addr");
        assertThat(found.get().getTimezone()).isEqualTo("UTC");
        assertThat(found.get().getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
        assertThat(found.get().getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    @DisplayName("findById returns empty when id unknown")
    void findByIdMiss() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("findByCode returns the persisted warehouse")
    void findByCodeHit() {
        Warehouse w = Warehouse.create("WH04", "ByCode", null, "UTC", ACTOR);
        adapter.insert(w);

        Optional<Warehouse> found = adapter.findByCode("WH04");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(w.getId());
    }

    @Test
    @DisplayName("update mutable fields; version bumps; immutable fields preserved")
    void updateMutableFields() {
        Warehouse inserted = adapter.insert(
                Warehouse.create("WH05", "Original", "Original Addr", "UTC", ACTOR));
        assertThat(inserted.getVersion()).isZero();

        Warehouse loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", "New Addr", "Asia/Seoul", "actor-2");
        adapter.update(loaded);

        Warehouse reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getAddress()).isEqualTo("New Addr");
        assertThat(reloaded.getTimezone()).isEqualTo("Asia/Seoul");
        assertThat(reloaded.getWarehouseCode()).isEqualTo("WH05");
        assertThat(reloaded.getCreatedBy()).isEqualTo(ACTOR);
        assertThat(reloaded.getUpdatedBy()).isEqualTo("actor-2");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("deactivate state persists")
    void deactivatePersists() {
        Warehouse w = adapter.insert(
                Warehouse.create("WH06", "For deactivation", null, "UTC", ACTOR));

        Warehouse loaded = adapter.findById(w.getId()).orElseThrow();
        loaded.deactivate("actor-2");
        adapter.update(loaded);

        Warehouse reloaded = adapter.findById(w.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        Warehouse w = adapter.insert(Warehouse.create("WH07", "Concurrent", null, "UTC", ACTOR));

        Warehouse firstLoad = adapter.findById(w.getId()).orElseThrow();
        Warehouse secondLoad = adapter.findById(w.getId()).orElseThrow();

        firstLoad.applyUpdate("Changed by first", null, null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Changed by second (stale)", null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage returns paginated, filtered, sorted results")
    void findPagePaginatesAndFilters() {
        adapter.insert(Warehouse.create("WH10", "Alpha",   null, "UTC", ACTOR));
        adapter.insert(Warehouse.create("WH11", "Bravo",   null, "UTC", ACTOR));
        adapter.insert(Warehouse.create("WH12", "Charlie", null, "UTC", ACTOR));

        Warehouse toDeactivate = adapter.findByCode("WH11").orElseThrow();
        toDeactivate.deactivate(ACTOR);
        adapter.update(toDeactivate);

        PageResult<Warehouse> activeOnly = adapter.findPage(
                new WarehouseListCriteria(WarehouseStatus.ACTIVE, null),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(activeOnly.totalElements()).isEqualTo(2);
        assertThat(activeOnly.content())
                .extracting(Warehouse::getWarehouseCode)
                .containsExactlyInAnyOrder("WH10", "WH12");

        PageResult<Warehouse> filteredByQuery = adapter.findPage(
                new WarehouseListCriteria(null, "Bravo"),
                new PageQuery(0, 10, "updatedAt", "desc"));

        assertThat(filteredByQuery.totalElements()).isEqualTo(1);
        assertThat(filteredByQuery.content().get(0).getWarehouseCode()).isEqualTo("WH11");

        PageResult<Warehouse> firstPage = adapter.findPage(
                WarehouseListCriteria.any(),
                new PageQuery(0, 2, "warehouseCode", "asc"));

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.content().get(0).getWarehouseCode()).isEqualTo("WH10");
    }

    @Test
    @DisplayName("hasActiveZonesFor returns false for a warehouse with no zones")
    void hasActiveZonesEmpty() {
        Warehouse w = adapter.insert(Warehouse.create("WH20", "Empty", null, "UTC", ACTOR));

        assertThat(adapter.hasActiveZonesFor(w.getId())).isFalse();
    }

    @Test
    @DisplayName("hasActiveZonesFor returns true when at least one ACTIVE zone exists")
    void hasActiveZonesTrue() {
        Warehouse w = adapter.insert(Warehouse.create("WH21", "WithZones", null, "UTC", ACTOR));
        insertZone(w.getId(), "Z-A", WarehouseStatus.ACTIVE);

        assertThat(adapter.hasActiveZonesFor(w.getId())).isTrue();
    }

    @Test
    @DisplayName("hasActiveZonesFor returns false when only INACTIVE zones exist")
    void hasActiveZonesAllInactive() {
        Warehouse w = adapter.insert(Warehouse.create("WH22", "AllClosed", null, "UTC", ACTOR));
        insertZone(w.getId(), "Z-A", WarehouseStatus.INACTIVE);
        insertZone(w.getId(), "Z-B", WarehouseStatus.INACTIVE);

        assertThat(adapter.hasActiveZonesFor(w.getId())).isFalse();
    }

    @Test
    @DisplayName("hasActiveZonesFor scopes to the queried warehouseId — sibling zones do not leak")
    void hasActiveZonesScopedToWarehouse() {
        Warehouse w1 = adapter.insert(Warehouse.create("WH23", "First", null, "UTC", ACTOR));
        Warehouse w2 = adapter.insert(Warehouse.create("WH24", "Second", null, "UTC", ACTOR));
        insertZone(w2.getId(), "Z-A", WarehouseStatus.ACTIVE);

        assertThat(adapter.hasActiveZonesFor(w1.getId())).isFalse();
        assertThat(adapter.hasActiveZonesFor(w2.getId())).isTrue();
    }

    private void insertZone(UUID warehouseId, String zoneCode, WarehouseStatus status) {
        Instant now = Instant.now();
        ZoneJpaEntity zone = new ZoneJpaEntity(
                UUID.randomUUID(),
                warehouseId,
                zoneCode,
                "Zone " + zoneCode,
                ZoneType.AMBIENT,
                status,
                null, // version=null so JPA treats as new
                now,
                ACTOR,
                now,
                ACTOR);
        jpaZoneRepository.saveAndFlush(zone);
    }
}
