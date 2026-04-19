package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.application.command.DeactivateLocationCommand;
import com.wms.master.application.command.ReactivateLocationCommand;
import com.wms.master.application.command.UpdateLocationCommand;
import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.event.LocationCreatedEvent;
import com.wms.master.domain.event.LocationDeactivatedEvent;
import com.wms.master.domain.event.LocationReactivatedEvent;
import com.wms.master.domain.event.LocationUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.exception.LocationNotFoundException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.Zone;
import com.wms.master.domain.model.ZoneType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocationServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";
    private static final String CODE = "WH01-A-01-01-01";

    private FakeLocationPersistencePort locationPersistence;
    private FakeZonePersistencePort zonePersistence;
    private FakeWarehousePersistencePort warehousePersistence;
    private FakeDomainEventPort events;
    private LocationService service;

    private UUID warehouseId;
    private UUID zoneId;

    @BeforeEach
    void setUp() {
        locationPersistence = new FakeLocationPersistencePort();
        zonePersistence = new FakeZonePersistencePort();
        warehousePersistence = new FakeWarehousePersistencePort();
        events = new FakeDomainEventPort();
        service = new LocationService(
                locationPersistence, zonePersistence, warehousePersistence, events);

        Warehouse wh = warehousePersistence.insert(
                Warehouse.create("WH01", "Seoul Main", null, "Asia/Seoul", "seed"));
        warehouseId = wh.getId();

        Zone zone = zonePersistence.insert(
                Zone.create(warehouseId, "Z-A", "Ambient A", ZoneType.AMBIENT, "seed"));
        zoneId = zone.getId();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists a new location and publishes Created event")
        void happyPath() {
            LocationResult result = service.create(sampleCreate(CODE));

            assertThat(result.id()).isNotNull();
            assertThat(result.warehouseId()).isEqualTo(warehouseId);
            assertThat(result.zoneId()).isEqualTo(zoneId);
            assertThat(result.locationCode()).isEqualTo(CODE);
            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isZero();

            LocationCreatedEvent event = events.single(LocationCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
            assertThat(event.actorId()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("unknown zoneId → ZoneNotFoundException")
        void unknownZone() {
            assertThatThrownBy(() -> service.create(new CreateLocationCommand(
                    warehouseId, UUID.randomUUID(), CODE,
                    null, null, null, null,
                    LocationType.STORAGE, null, ACTOR)))
                    .isInstanceOf(ZoneNotFoundException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("parent zone belongs to different warehouse → ZoneNotFoundException (leak-safe)")
        void zoneWarehouseMismatch() {
            // seed a different warehouse + zone pair, then attempt to create under wrong path
            Warehouse other = warehousePersistence.insert(
                    Warehouse.create("WH02", "Busan", null, "UTC", "seed"));
            Zone otherZone = zonePersistence.insert(
                    Zone.create(other.getId(), "Z-B", "B", ZoneType.AMBIENT, "seed"));

            // path says warehouseId=WH01.id but zoneId belongs to WH02
            assertThatThrownBy(() -> service.create(new CreateLocationCommand(
                    warehouseId, otherZone.getId(), CODE,
                    null, null, null, null,
                    LocationType.STORAGE, null, ACTOR)))
                    .isInstanceOf(ZoneNotFoundException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("inactive parent zone → InvalidStateTransitionException")
        void inactiveParentZone() {
            Zone stored = zonePersistence.stored(zoneId);
            stored.deactivate("admin");
            zonePersistence.update(stored);

            assertThatThrownBy(() -> service.create(sampleCreate(CODE)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("parent zone is not ACTIVE");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("missing parent warehouse → WarehouseNotFoundException")
        void missingParentWarehouse() {
            // Zone says it belongs to an invented warehouseId by reconstituting
            // a Zone whose warehouseId matches what we pass but isn't in the
            // warehouse fake.
            UUID ghostWarehouseId = UUID.randomUUID();
            Zone ghostZone = Zone.reconstitute(
                    UUID.randomUUID(), ghostWarehouseId, "Z-G", "G",
                    ZoneType.AMBIENT, WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), "seed",
                    java.time.Instant.now(), "seed");
            zonePersistence.insert(ghostZone);

            assertThatThrownBy(() -> service.create(new CreateLocationCommand(
                    ghostWarehouseId, ghostZone.getId(), "WH01-G-01-01-01",
                    null, null, null, null,
                    LocationType.STORAGE, null, ACTOR)))
                    .isInstanceOf(WarehouseNotFoundException.class);
        }

        @Test
        @DisplayName("locationCode prefix mismatch → ValidationException")
        void prefixMismatch() {
            // Parent warehouse is WH01, but the code says WH02 — caught by the
            // domain factory via the warehouseCode parameter.
            assertThatThrownBy(() -> service.create(new CreateLocationCommand(
                    warehouseId, zoneId, "WH02-A-01-01-01",
                    null, null, null, null,
                    LocationType.STORAGE, null, ACTOR)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("WH01-");
        }

        @Test
        @DisplayName("duplicate locationCode → LocationCodeDuplicateException")
        void duplicateCode() {
            service.create(sampleCreate(CODE));

            assertThatThrownBy(() -> service.create(sampleCreate(CODE)))
                    .isInstanceOf(LocationCodeDuplicateException.class)
                    .hasMessageContaining(CODE);

            assertThat(events.published()).hasSize(1);
        }

        @Test
        @DisplayName("domain validation failure (bad pattern) → ValidationException")
        void badPattern() {
            assertThatThrownBy(() -> service.create(new CreateLocationCommand(
                    warehouseId, zoneId, "bad-code",
                    null, null, null, null,
                    LocationType.STORAGE, null, ACTOR)))
                    .isInstanceOf(ValidationException.class);

            assertThat(locationPersistence.size()).isZero();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mutable fields change, event lists changed fields")
        void happyPath() {
            LocationResult created = service.create(sampleCreate(CODE));
            events.clear();

            LocationResult updated = service.update(new UpdateLocationCommand(
                    created.id(), LocationType.DAMAGED, 999,
                    "09", null, null, null,
                    null, null, null,
                    created.version(), ACTOR_2));

            assertThat(updated.locationType()).isEqualTo(LocationType.DAMAGED);
            assertThat(updated.capacityUnits()).isEqualTo(999);
            assertThat(updated.aisle()).isEqualTo("09");
            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(updated.updatedBy()).isEqualTo(ACTOR_2);

            LocationUpdatedEvent event = events.single(LocationUpdatedEvent.class);
            assertThat(event.changedFields())
                    .containsExactlyInAnyOrder("locationType", "capacityUnits", "aisle");
        }

        @Test
        @DisplayName("no-op update bumps version but emits no event")
        void noOp() {
            LocationResult created = service.create(sampleCreate(CODE));
            events.clear();

            LocationResult updated = service.update(new UpdateLocationCommand(
                    created.id(), null, null, null, null, null, null,
                    null, null, null, created.version(), ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("locationCode mutation attempt → ImmutableFieldException")
        void rejectsCodeMutation() {
            LocationResult created = service.create(sampleCreate(CODE));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    created.id(), null, null, null, null, null, null,
                    "WH01-A-09-09-09", null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("locationCode");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("warehouseId mutation attempt → ImmutableFieldException")
        void rejectsWarehouseIdMutation() {
            LocationResult created = service.create(sampleCreate(CODE));

            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    created.id(), null, null, null, null, null, null,
                    null, UUID.randomUUID(), null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("warehouseId");
        }

        @Test
        @DisplayName("zoneId mutation attempt → ImmutableFieldException")
        void rejectsZoneIdMutation() {
            LocationResult created = service.create(sampleCreate(CODE));

            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    created.id(), null, null, null, null, null, null,
                    null, null, UUID.randomUUID(),
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("unknown id → LocationNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    UUID.randomUUID(), LocationType.STORAGE, null,
                    null, null, null, null,
                    null, null, null, 0L, ACTOR)))
                    .isInstanceOf(LocationNotFoundException.class);
        }

        @Test
        @DisplayName("stale version → ConcurrencyConflictException before save")
        void staleVersion() {
            LocationResult created = service.create(sampleCreate(CODE));
            service.update(new UpdateLocationCommand(
                    created.id(), LocationType.DAMAGED, null,
                    null, null, null, null,
                    null, null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    created.id(), LocationType.STORAGE, null,
                    null, null, null, null,
                    null, null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLock() {
            LocationResult created = service.create(sampleCreate(CODE));
            events.clear();
            locationPersistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdateLocationCommand(
                    created.id(), LocationType.DAMAGED, null,
                    null, null, null, null,
                    null, null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("active location becomes inactive; event carries reason")
        void happyPath() {
            LocationResult created = service.create(sampleCreate(CODE));
            events.clear();

            LocationResult result = service.deactivate(new DeactivateLocationCommand(
                    created.id(), "closing", created.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(result.version()).isEqualTo(created.version() + 1);

            LocationDeactivatedEvent event = events.single(LocationDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("closing");
        }

        @Test
        @DisplayName("already-INACTIVE raises InvalidStateTransitionException")
        void alreadyInactive() {
            LocationResult created = service.create(sampleCreate(CODE));
            LocationResult deactivated = service.deactivate(new DeactivateLocationCommand(
                    created.id(), "first", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateLocationCommand(
                    created.id(), "second", deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("inactive location becomes active when parent zone still ACTIVE")
        void happyPath() {
            LocationResult created = service.create(sampleCreate(CODE));
            LocationResult deactivated = service.deactivate(new DeactivateLocationCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            LocationResult result = service.reactivate(new ReactivateLocationCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(events.single(LocationReactivatedEvent.class).aggregateId())
                    .isEqualTo(created.id());
        }

        @Test
        @DisplayName("reactivating active location throws")
        void reactivateActive() {
            LocationResult created = service.create(sampleCreate(CODE));

            assertThatThrownBy(() -> service.reactivate(new ReactivateLocationCommand(
                    created.id(), created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("parent zone inactive → InvalidStateTransitionException")
        void parentZoneInactive() {
            LocationResult created = service.create(sampleCreate(CODE));
            LocationResult deactivated = service.deactivate(new DeactivateLocationCommand(
                    created.id(), "r", created.version(), ACTOR));

            Zone zoneStored = zonePersistence.stored(zoneId);
            zoneStored.deactivate("admin");
            zonePersistence.update(zoneStored);
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivateLocationCommand(
                    created.id(), deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("parent zone is not ACTIVE");

            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted location")
        void findByIdHit() {
            LocationResult created = service.create(sampleCreate(CODE));

            LocationResult found = service.findById(created.id());

            assertThat(found.id()).isEqualTo(created.id());
            assertThat(found.locationCode()).isEqualTo(CODE);
        }

        @Test
        @DisplayName("findById on unknown id → LocationNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(LocationNotFoundException.class);
        }
    }

    private CreateLocationCommand sampleCreate(String code) {
        return new CreateLocationCommand(
                warehouseId, zoneId, code,
                null, null, null, null,
                LocationType.STORAGE, 500, ACTOR);
    }
}
