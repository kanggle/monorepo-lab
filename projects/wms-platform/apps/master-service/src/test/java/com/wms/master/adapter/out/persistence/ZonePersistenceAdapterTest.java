package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.Zone;
import com.wms.master.domain.model.ZoneType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-Postgres slice test for Zone persistence. Runs the Flyway migrations
 * (V1 warehouses, V2 outbox, V3 zones) so the compound-unique constraint, FK,
 * and enum check are exercised end-to-end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        WarehousePersistenceMapper.class,
        WarehousePersistenceAdapter.class,
        ZonePersistenceMapper.class,
        ZonePersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class ZonePersistenceAdapterTest {

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
    ZonePersistencePort adapter;

    @Autowired
    com.wms.master.application.port.out.WarehousePersistencePort warehouseAdapter;

    @Autowired
    JpaZoneRepository jpaRepository;

    private UUID seedWarehouseAndGetId(String code) {
        Warehouse parent = warehouseAdapter.insert(
                Warehouse.create(code, "Parent " + code, null, "UTC", ACTOR));
        return parent.getId();
    }

    @Test
    @DisplayName("insert persists a new zone and assigns version 0")
    void insertPersistsNew() {
        UUID warehouseId = seedWarehouseAndGetId("WH01");
        Zone created = Zone.create(warehouseId, "Z-A", "Ambient", ZoneType.AMBIENT, ACTOR);

        Zone saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("compound unique: duplicate (warehouseId, zoneCode) → ZoneCodeDuplicateException")
    void insertDuplicateCompoundKey() {
        UUID warehouseId = seedWarehouseAndGetId("WH02");
        adapter.insert(Zone.create(warehouseId, "Z-A", "First", ZoneType.AMBIENT, ACTOR));

        Zone duplicate = Zone.create(warehouseId, "Z-A", "Second", ZoneType.CHILLED, ACTOR);
        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(ZoneCodeDuplicateException.class)
                .hasMessageContaining("Z-A");
    }

    @Test
    @DisplayName("same zoneCode in different warehouses both succeed")
    void sameCodeDifferentWarehouseOk() {
        UUID wh1 = seedWarehouseAndGetId("WH03");
        UUID wh2 = seedWarehouseAndGetId("WH04");

        adapter.insert(Zone.create(wh1, "Z-A", "First", ZoneType.AMBIENT, ACTOR));
        Zone second = adapter.insert(Zone.create(wh2, "Z-A", "Second", ZoneType.AMBIENT, ACTOR));

        assertThat(second.getWarehouseId()).isEqualTo(wh2);
    }

    @Test
    @DisplayName("findById returns the persisted zone")
    void findByIdHit() {
        UUID warehouseId = seedWarehouseAndGetId("WH05");
        Zone z = Zone.create(warehouseId, "Z-A", "Name", ZoneType.FROZEN, ACTOR);
        adapter.insert(z);

        Optional<Zone> found = adapter.findById(z.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getZoneCode()).isEqualTo("Z-A");
        assertThat(found.get().getZoneType()).isEqualTo(ZoneType.FROZEN);
        assertThat(found.get().getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
    }

    @Test
    @DisplayName("findByWarehouseIdAndZoneCode returns the persisted zone")
    void findByCompoundKey() {
        UUID warehouseId = seedWarehouseAndGetId("WH06");
        Zone z = Zone.create(warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR);
        adapter.insert(z);

        Optional<Zone> found = adapter.findByWarehouseIdAndZoneCode(warehouseId, "Z-A");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(z.getId());
    }

    @Test
    @DisplayName("update mutable fields; version bumps; immutable fields preserved")
    void updateMutableFields() {
        UUID warehouseId = seedWarehouseAndGetId("WH07");
        Zone z = Zone.create(warehouseId, "Z-A", "Original", ZoneType.AMBIENT, ACTOR);
        Zone inserted = adapter.insert(z);
        assertThat(inserted.getVersion()).isZero();

        Zone loaded = adapter.findById(z.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", ZoneType.CHILLED, "actor-2");
        adapter.update(loaded);

        Zone reloaded = adapter.findById(z.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getZoneType()).isEqualTo(ZoneType.CHILLED);
        assertThat(reloaded.getZoneCode()).isEqualTo("Z-A");
        assertThat(reloaded.getWarehouseId()).isEqualTo(warehouseId);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        UUID warehouseId = seedWarehouseAndGetId("WH08");
        Zone z = Zone.create(warehouseId, "Z-A", "Concurrent", ZoneType.AMBIENT, ACTOR);
        adapter.insert(z);

        Zone firstLoad = adapter.findById(z.getId()).orElseThrow();
        Zone secondLoad = adapter.findById(z.getId()).orElseThrow();

        firstLoad.applyUpdate("Changed by first", null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Changed by second (stale)", null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage filters by warehouse, status, zoneType; paginates")
    void findPageFiltersAndPaginates() {
        UUID wh1 = seedWarehouseAndGetId("WH09");
        UUID wh2 = seedWarehouseAndGetId("WH10");

        adapter.insert(Zone.create(wh1, "Z-A", "Alpha",   ZoneType.AMBIENT, ACTOR));
        adapter.insert(Zone.create(wh1, "Z-B", "Bravo",   ZoneType.CHILLED, ACTOR));
        adapter.insert(Zone.create(wh1, "Z-C", "Charlie", ZoneType.AMBIENT, ACTOR));
        adapter.insert(Zone.create(wh2, "Z-A", "Other",   ZoneType.AMBIENT, ACTOR));

        PageResult<Zone> wh1Ambient = adapter.findPage(
                new ListZonesCriteria(wh1, null, ZoneType.AMBIENT),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(wh1Ambient.totalElements()).isEqualTo(2);
        assertThat(wh1Ambient.content())
                .extracting(Zone::getZoneCode)
                .containsExactlyInAnyOrder("Z-A", "Z-C");

        PageResult<Zone> wh2All = adapter.findPage(
                ListZonesCriteria.forWarehouse(wh2),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(wh2All.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("hasActiveLocationsFor stub returns false in v1")
    void hasActiveLocationsStub() {
        UUID warehouseId = seedWarehouseAndGetId("WH11");
        Zone z = adapter.insert(Zone.create(warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));

        assertThat(adapter.hasActiveLocationsFor(z.getId())).isFalse();
    }
}
