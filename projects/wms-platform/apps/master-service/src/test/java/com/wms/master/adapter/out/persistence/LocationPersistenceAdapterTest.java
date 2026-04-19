package com.wms.master.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.port.out.ZonePersistencePort;
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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-Postgres slice test for Location persistence. Runs the Flyway migrations
 * (V1 warehouses, V2 outbox, V3 zones, V4 locations) so the global-unique
 * constraint, FKs, and enum / capacity checks are exercised end-to-end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MasterServicePersistenceConfig.class,
        WarehousePersistenceMapper.class,
        WarehousePersistenceAdapter.class,
        ZonePersistenceMapper.class,
        ZonePersistenceAdapter.class,
        LocationPersistenceMapper.class,
        LocationPersistenceAdapter.class})
@Testcontainers(disabledWithoutDocker = true)
class LocationPersistenceAdapterTest {

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
    LocationPersistencePort adapter;

    @Autowired
    ZonePersistencePort zoneAdapter;

    @Autowired
    WarehousePersistencePort warehouseAdapter;

    private ZoneHandle seedWarehouseAndZone(String warehouseCode) {
        return seedWarehouseAndZone(warehouseCode, "Z-A");
    }

    private ZoneHandle seedWarehouseAndZone(String warehouseCode, String zoneCode) {
        Warehouse parent = warehouseAdapter.findByCode(warehouseCode)
                .orElseGet(() -> warehouseAdapter.insert(
                        Warehouse.create(warehouseCode, "Parent " + warehouseCode, null, "UTC", ACTOR)));
        Zone zone = zoneAdapter.insert(
                Zone.create(parent.getId(), zoneCode, "Zone " + zoneCode, ZoneType.AMBIENT, ACTOR));
        return new ZoneHandle(warehouseCode, parent.getId(), zone.getId());
    }

    @Test
    @DisplayName("insert persists a new location and assigns version 0")
    void insertPersistsNew() {
        ZoneHandle z = seedWarehouseAndZone("WH01");
        Location created = Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH01-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, 500, ACTOR);

        Location saved = adapter.insert(created);

        assertThat(saved.getId()).isEqualTo(created.getId());
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @DisplayName("duplicate locationCode across zones (same warehouse) → LocationCodeDuplicateException (W3 global)")
    void duplicateAcrossZones() {
        // Two zones under the same warehouse — prefix matches, so both inserts
        // pass domain validation. The DB's global UNIQUE(location_code) rejects
        // the second insert, proving the code is unique system-wide regardless
        // of the parent zone.
        ZoneHandle z1 = seedWarehouseAndZone("WH02", "Z-A");
        ZoneHandle z2 = seedWarehouseAndZone("WH02", "Z-B");

        adapter.insert(Location.create(
                z1.warehouseCode, z1.warehouseId, z1.zoneId,
                "WH02-X-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        Location dup = Location.create(
                z2.warehouseCode, z2.warehouseId, z2.zoneId,
                "WH02-X-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR);

        assertThatThrownBy(() -> adapter.insert(dup))
                .isInstanceOf(LocationCodeDuplicateException.class);
    }

    @Test
    @DisplayName("zone deactivate guard: hasActiveLocationsFor reflects real state")
    void zoneGuard() {
        ZoneHandle z = seedWarehouseAndZone("WH04");
        Location loc = adapter.insert(Location.create(
                z.warehouseCode, z.warehouseId, z.zoneId,
                "WH04-A-01-01-01", null, null, null, null,
                LocationType.STORAGE, null, ACTOR));

        assertThat(zoneAdapter.hasActiveLocationsFor(z.zoneId)).isTrue();

        Location loaded = adapter.findById(loc.getId()).orElseThrow();
        loaded.deactivate(ACTOR);
        adapter.update(loaded);

        assertThat(zoneAdapter.hasActiveLocationsFor(z.zoneId)).isFalse();
    }

    private record ZoneHandle(String warehouseCode, UUID warehouseId, UUID zoneId) {
    }
}
