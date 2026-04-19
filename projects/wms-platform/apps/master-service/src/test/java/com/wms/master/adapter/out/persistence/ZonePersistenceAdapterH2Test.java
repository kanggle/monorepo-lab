package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.Zone;
import com.wms.master.domain.model.ZoneType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

/**
 * H2-backed mirror of {@link ZonePersistenceAdapterTest}. Covers the compound
 * unique constraint, the insert-path ({@code version=null} mapper), and
 * optimistic locking on every CI run regardless of Docker availability. The
 * Testcontainers version remains authoritative for Flyway against real
 * Postgres.
 */
@DataJpaTest
@Import({MasterServicePersistenceConfig.class,
        WarehousePersistenceMapper.class,
        WarehousePersistenceAdapter.class,
        ZonePersistenceMapper.class,
        ZonePersistenceAdapter.class,
        LocationPersistenceMapper.class,
        LocationPersistenceAdapter.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:zone_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class ZonePersistenceAdapterH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    ZonePersistencePort adapter;

    @Autowired
    WarehousePersistencePort warehouseAdapter;

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
                .isInstanceOf(ZoneCodeDuplicateException.class);
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
    @DisplayName("update mutable fields; version bumps")
    void updateMutableFields() {
        UUID warehouseId = seedWarehouseAndGetId("WH05");
        Zone inserted = adapter.insert(
                Zone.create(warehouseId, "Z-A", "Original", ZoneType.AMBIENT, ACTOR));
        assertThat(inserted.getVersion()).isZero();

        Zone loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate("Renamed", ZoneType.CHILLED, "actor-2");
        adapter.update(loaded);

        Zone reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Renamed");
        assertThat(reloaded.getZoneType()).isEqualTo(ZoneType.CHILLED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("optimistic locking collision surfaces as ObjectOptimisticLockingFailureException")
    void optimisticLockCollision() {
        UUID warehouseId = seedWarehouseAndGetId("WH06");
        Zone z = adapter.insert(Zone.create(warehouseId, "Z-A", "C", ZoneType.AMBIENT, ACTOR));

        Zone firstLoad = adapter.findById(z.getId()).orElseThrow();
        Zone secondLoad = adapter.findById(z.getId()).orElseThrow();

        firstLoad.applyUpdate("First", null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate("Second (stale)", null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findPage filters by warehouse and zoneType")
    void findPageFilters() {
        UUID wh1 = seedWarehouseAndGetId("WH07");
        UUID wh2 = seedWarehouseAndGetId("WH08");

        adapter.insert(Zone.create(wh1, "Z-A", "A", ZoneType.AMBIENT, ACTOR));
        adapter.insert(Zone.create(wh1, "Z-B", "B", ZoneType.CHILLED, ACTOR));
        adapter.insert(Zone.create(wh2, "Z-A", "O", ZoneType.AMBIENT, ACTOR));

        PageResult<Zone> wh1Ambient = adapter.findPage(
                new ListZonesCriteria(wh1, null, ZoneType.AMBIENT),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(wh1Ambient.totalElements()).isEqualTo(1);

        PageResult<Zone> wh1All = adapter.findPage(
                ListZonesCriteria.forWarehouse(wh1),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(wh1All.totalElements()).isEqualTo(2);
    }
}
