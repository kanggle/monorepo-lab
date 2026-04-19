package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.application.command.DeactivateZoneCommand;
import com.wms.master.application.command.ReactivateZoneCommand;
import com.wms.master.application.command.UpdateZoneCommand;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.application.query.ListZonesQuery;
import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.event.ZoneCreatedEvent;
import com.wms.master.domain.event.ZoneDeactivatedEvent;
import com.wms.master.domain.event.ZoneReactivatedEvent;
import com.wms.master.domain.event.ZoneUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ZoneServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    private FakeZonePersistencePort zonePersistence;
    private FakeWarehousePersistencePort warehousePersistence;
    private FakeDomainEventPort events;
    private ZoneService service;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        zonePersistence = new FakeZonePersistencePort();
        warehousePersistence = new FakeWarehousePersistencePort();
        events = new FakeDomainEventPort();
        service = new ZoneService(zonePersistence, warehousePersistence, events);

        // Every test starts with an ACTIVE parent warehouse WH01.
        Warehouse wh = warehousePersistence.insert(
                Warehouse.create("WH01", "Seoul Main", null, "Asia/Seoul", "seed"));
        warehouseId = wh.getId();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists a new zone at version 0 and publishes Created event")
        void happyPath() {
            ZoneResult result = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Ambient A", ZoneType.AMBIENT, ACTOR));

            assertThat(result.id()).isNotNull();
            assertThat(result.warehouseId()).isEqualTo(warehouseId);
            assertThat(result.zoneCode()).isEqualTo("Z-A");
            assertThat(result.zoneType()).isEqualTo(ZoneType.AMBIENT);
            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isZero();

            ZoneCreatedEvent event = events.single(ZoneCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
            assertThat(event.actorId()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("rejects duplicate zoneCode within the same warehouse")
        void duplicateCodeSameWarehouse() {
            service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "First", ZoneType.AMBIENT, ACTOR));

            assertThatThrownBy(() -> service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Second", ZoneType.CHILLED, ACTOR)))
                    .isInstanceOf(ZoneCodeDuplicateException.class)
                    .hasMessageContaining("Z-A");

            assertThat(events.published()).hasSize(1);
        }

        @Test
        @DisplayName("same zoneCode in a different warehouse succeeds")
        void sameCodeDifferentWarehouse() {
            Warehouse other = warehousePersistence.insert(
                    Warehouse.create("WH02", "Other", null, "UTC", "seed"));

            service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "First", ZoneType.AMBIENT, ACTOR));
            ZoneResult second = service.create(new CreateZoneCommand(
                    other.getId(), "Z-A", "Second", ZoneType.CHILLED, ACTOR));

            assertThat(second.warehouseId()).isEqualTo(other.getId());
            assertThat(zonePersistence.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("unknown warehouseId → WarehouseNotFoundException")
        void unknownWarehouseId() {
            assertThatThrownBy(() -> service.create(new CreateZoneCommand(
                    UUID.randomUUID(), "Z-A", "Name", ZoneType.AMBIENT, ACTOR)))
                    .isInstanceOf(WarehouseNotFoundException.class);

            assertThat(zonePersistence.size()).isZero();
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("inactive parent warehouse → InvalidStateTransitionException")
        void inactiveParentWarehouse() {
            deactivateParentWarehouse();

            assertThatThrownBy(() -> service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("parent warehouse is not ACTIVE");

            assertThat(zonePersistence.size()).isZero();
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("domain validation failures bubble up unchanged")
        void validationFailure() {
            assertThatThrownBy(() -> service.create(new CreateZoneCommand(
                    warehouseId, "bad-code", "Name", ZoneType.AMBIENT, ACTOR)))
                    .isInstanceOf(ValidationException.class);
            assertThat(zonePersistence.size()).isZero();
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mutable fields change, version arg matches stored, event lists changed fields")
        void updatesMutableFields() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Original", ZoneType.AMBIENT, ACTOR));
            events.clear();

            ZoneResult updated = service.update(new UpdateZoneCommand(
                    created.id(),
                    "Renamed",
                    ZoneType.CHILLED,
                    null,
                    null,
                    created.version(),
                    ACTOR_2));

            assertThat(updated.name()).isEqualTo("Renamed");
            assertThat(updated.zoneType()).isEqualTo(ZoneType.CHILLED);
            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(updated.updatedBy()).isEqualTo(ACTOR_2);

            ZoneUpdatedEvent event = events.single(ZoneUpdatedEvent.class);
            assertThat(event.changedFields())
                    .containsExactlyInAnyOrder("name", "zoneType");
        }

        @Test
        @DisplayName("no-op update still saves but emits no event")
        void noChangesNoEvent() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Same", ZoneType.AMBIENT, ACTOR));
            events.clear();

            ZoneResult updated = service.update(new UpdateZoneCommand(
                    created.id(), "Same", ZoneType.AMBIENT, null, null,
                    created.version(), ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("zoneCode mutation attempt raises ImmutableFieldException")
        void rejectsZoneCodeMutation() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateZoneCommand(
                    created.id(), "Renamed", null, "Z-B", null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("zoneCode");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("warehouseId mutation attempt raises ImmutableFieldException")
        void rejectsWarehouseIdMutation() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateZoneCommand(
                    created.id(), "Renamed", null, null, UUID.randomUUID(),
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("warehouseId");
        }

        @Test
        @DisplayName("unknown id yields ZoneNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdateZoneCommand(
                    UUID.randomUUID(), "n", null, null, null, 0L, ACTOR)))
                    .isInstanceOf(ZoneNotFoundException.class);
        }

        @Test
        @DisplayName("stale caller version yields ConcurrencyConflictException before save")
        void staleVersionConflict() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            service.update(new UpdateZoneCommand(
                    created.id(), "Name-2", null, null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateZoneCommand(
                    created.id(), "Will Fail", null, null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class)
                    .hasMessageContaining("version 0")
                    .hasMessageContaining("current version 1");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLockTranslated() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();
            zonePersistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdateZoneCommand(
                    created.id(), "Renamed", null, null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);

            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("active zone becomes inactive, version bumps, event carries reason")
        void happyPath() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();

            ZoneResult result = service.deactivate(new DeactivateZoneCommand(
                    created.id(), "Closing", created.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(result.version()).isEqualTo(created.version() + 1);

            ZoneDeactivatedEvent event = events.single(ZoneDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("Closing");
        }

        @Test
        @DisplayName("blocked when zone has active locations")
        void blockedByActiveLocations() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();
            zonePersistence.markZoneAsHavingActiveLocations(created.id());

            assertThatThrownBy(() -> service.deactivate(new DeactivateZoneCommand(
                    created.id(), "r", created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("zone has active locations");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("already-INACTIVE zone raises InvalidStateTransitionException")
        void alreadyInactive() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            ZoneResult deactivated = service.deactivate(new DeactivateZoneCommand(
                    created.id(), "first", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateZoneCommand(
                    created.id(), "second", deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("stale version raises ConcurrencyConflictException")
        void staleVersion() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            service.update(new UpdateZoneCommand(
                    created.id(), "Renamed", null, null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateZoneCommand(
                    created.id(), "reason", created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("inactive zone becomes active and Reactivated event published")
        void happyPath() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            ZoneResult deactivated = service.deactivate(new DeactivateZoneCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            ZoneResult result = service.reactivate(new ReactivateZoneCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isEqualTo(deactivated.version() + 1);
            assertThat(events.single(ZoneReactivatedEvent.class).aggregateId())
                    .isEqualTo(created.id());
        }

        @Test
        @DisplayName("reactivating an active zone raises InvalidStateTransitionException")
        void alreadyActive() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivateZoneCommand(
                    created.id(), created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate under INACTIVE parent raises InvalidStateTransitionException")
        void inactiveParent() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));
            ZoneResult deactivated = service.deactivate(new DeactivateZoneCommand(
                    created.id(), "r", created.version(), ACTOR));

            deactivateParentWarehouse();
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivateZoneCommand(
                    created.id(), deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("parent warehouse is not ACTIVE");

            assertThat(events.published()).isEmpty();
        }
    }

    private void deactivateParentWarehouse() {
        Warehouse stored = warehousePersistence.stored(warehouseId);
        stored.deactivate("admin");
        warehousePersistence.update(stored);
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted zone")
        void findByIdHit() {
            ZoneResult created = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Name", ZoneType.AMBIENT, ACTOR));

            ZoneResult found = service.findById(created.id());

            assertThat(found.id()).isEqualTo(created.id());
            assertThat(found.zoneCode()).isEqualTo("Z-A");
        }

        @Test
        @DisplayName("findById on unknown id raises ZoneNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(ZoneNotFoundException.class);
        }

        @Test
        @DisplayName("list filters by status and zoneType and paginates")
        void listFiltersAndPaginates() {
            ZoneResult a = service.create(new CreateZoneCommand(
                    warehouseId, "Z-A", "Alpha", ZoneType.AMBIENT, ACTOR));
            service.create(new CreateZoneCommand(
                    warehouseId, "Z-B", "Bravo", ZoneType.CHILLED, ACTOR));
            service.create(new CreateZoneCommand(
                    warehouseId, "Z-C", "Charlie", ZoneType.AMBIENT, ACTOR));
            service.deactivate(new DeactivateZoneCommand(a.id(), "r", a.version(), ACTOR));

            PageResult<ZoneResult> active = service.list(new ListZonesQuery(
                    new ListZonesCriteria(warehouseId, WarehouseStatus.ACTIVE, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(active.totalElements()).isEqualTo(2);

            PageResult<ZoneResult> ambient = service.list(new ListZonesQuery(
                    new ListZonesCriteria(warehouseId, null, ZoneType.AMBIENT),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(ambient.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("list under unknown warehouse → WarehouseNotFoundException")
        void listUnknownWarehouse() {
            assertThatThrownBy(() -> service.list(new ListZonesQuery(
                    ListZonesCriteria.forWarehouse(UUID.randomUUID()),
                    new PageQuery(0, 10, "updatedAt", "desc"))))
                    .isInstanceOf(WarehouseNotFoundException.class);
        }
    }
}
