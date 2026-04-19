package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.config.MasterServicePersistenceConfig;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.model.Location;
import com.wms.master.domain.model.LocationType;
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
 * H2-backed slice test for Location persistence. Covers the global unique
 * constraint on {@code locationCode}, the insert-path ({@code version=null}
 * mapper), and optimistic locking on every CI run regardless of Docker.
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
        "spring.datasource.url=jdbc:h2:mem:location_adapter_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class LocationPersistenceAdapterH2Test {

    private static final String ACTOR = "actor-uuid";

    @Autowired
    LocationPersistencePort adapter;

    @Autowired
    ZonePersistencePort zoneAdapter;

    @Autowired
    WarehousePersistencePort warehouseAdapter;

    @Autowired
    JpaLocationRepository jpaRepository;

    private ZoneHandle seedWarehouseAndZone(String warehouseCode) {
        Warehouse parent = warehouseAdapter.insert(
                Warehouse.create(warehouseCode, "Parent " + warehouseCode, null, "UTC", ACTOR));
        Zone zone = zoneAdapter.insert(
                Zone.create(parent.getId(), "Z-A", "Ambient", ZoneType.AMBIENT, ACTOR));
        return new ZoneHandle(warehouseCode, parent.getId(), zone.getId());
    }

    @Test
    @DisplayName("insert persists a new location and assigns version 0")
    void insertPersistsNew() {
        ZoneHandle z = seedWarehouseAndZone("WH01");
        Location created = Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH01-A-01-01-01", "01", "01", "01", null,
                LocationType.STORAGE, 500, ACTOR);

        Location saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
        assertThat(jpaRepository.findById(created.getId())).isPresent();
    }

    @Test
    @DisplayName("global unique: duplicate locationCode → LocationCodeDuplicateException")
    void globalUniqueCode() {
        ZoneHandle z = seedWarehouseAndZone("WH02");
        adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH02-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        Location duplicate = Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH02-A-01-01-01", null, null, null, null,
                LocationType.DAMAGED, null, ACTOR);
        assertThatThrownBy(() -> adapter.insert(duplicate))
                .isInstanceOf(LocationCodeDuplicateException.class);
    }

    @Test
    @DisplayName("update mutable fields; version bumps")
    void updateMutableFields() {
        ZoneHandle z = seedWarehouseAndZone("WH03");
        Location inserted = adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH03-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, 100, ACTOR));
        assertThat(inserted.getVersion()).isZero();

        Location loaded = adapter.findById(inserted.getId()).orElseThrow();
        loaded.applyUpdate(LocationType.DAMAGED, 99, "09", null, null, null, "actor-2");
        adapter.update(loaded);

        Location reloaded = adapter.findById(inserted.getId()).orElseThrow();
        assertThat(reloaded.getLocationType()).isEqualTo(LocationType.DAMAGED);
        assertThat(reloaded.getCapacityUnits()).isEqualTo(99);
        assertThat(reloaded.getAisle()).isEqualTo("09");
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("optimistic locking collision → ObjectOptimisticLockingFailureException")
    void optimisticLock() {
        ZoneHandle z = seedWarehouseAndZone("WH04");
        Location loc = adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH04-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        Location firstLoad = adapter.findById(loc.getId()).orElseThrow();
        Location secondLoad = adapter.findById(loc.getId()).orElseThrow();

        firstLoad.applyUpdate(LocationType.DAMAGED, null, null, null, null, null, "actor-first");
        adapter.update(firstLoad);

        secondLoad.applyUpdate(LocationType.QUARANTINE, null, null, null, null, null, "actor-second");
        assertThatThrownBy(() -> adapter.update(secondLoad))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("findByLocationCode returns the persisted location")
    void findByCode() {
        ZoneHandle z = seedWarehouseAndZone("WH05");
        Location loc = adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH05-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        assertThat(adapter.findByLocationCode("WH05-A-01-01-01"))
                .isPresent()
                .get().extracting(Location::getId).isEqualTo(loc.getId());
    }

    @Test
    @DisplayName("findPage filters by warehouse, zone, status, type, code")
    void findPageFilters() {
        ZoneHandle z1 = seedWarehouseAndZone("WH06");
        ZoneHandle z2 = seedWarehouseAndZone("WH07");

        adapter.insert(Location.create(
                z1.warehouseCode, z1.warehouseId, z1.zoneId,
                "WH06-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));
        adapter.insert(Location.create(
                z1.warehouseCode, z1.warehouseId, z1.zoneId,
                "WH06-A-02-01-01", null, null, null, null,
                LocationType.DAMAGED, null, ACTOR));
        adapter.insert(Location.create(
                z2.warehouseCode, z2.warehouseId, z2.zoneId,
                "WH07-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        PageResult<Location> byWarehouse = adapter.findPage(
                new ListLocationsCriteria(z1.warehouseId, null, null, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(byWarehouse.totalElements()).isEqualTo(2);

        PageResult<Location> byType = adapter.findPage(
                new ListLocationsCriteria(null, null, LocationType.DAMAGED, null, null),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(byType.totalElements()).isEqualTo(1);

        PageResult<Location> byCode = adapter.findPage(
                new ListLocationsCriteria(null, null, null, "WH07-A-01-01-01", null),
                new PageQuery(0, 10, "updatedAt", "desc"));
        assertThat(byCode.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("hasActiveLocationsFor is TRUE when ACTIVE child exists; FALSE after deactivate")
    void zoneGuardReal() {
        ZoneHandle z = seedWarehouseAndZone("WH08");
        Location loc = adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH08-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        // Phase 5: real query, not the old stub.
        assertThat(zoneAdapter.hasActiveLocationsFor(z.zoneId)).isTrue();

        Location loaded = adapter.findById(loc.getId()).orElseThrow();
        loaded.deactivate(ACTOR);
        adapter.update(loaded);

        assertThat(zoneAdapter.hasActiveLocationsFor(z.zoneId)).isFalse();
    }

    private record ZoneHandle(String warehouseCode, UUID warehouseId, UUID zoneId) {
    }
}
