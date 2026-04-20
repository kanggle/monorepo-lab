package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateWarehouseCommand;
import com.wms.master.application.command.DeactivateWarehouseCommand;
import com.wms.master.application.command.ReactivateWarehouseCommand;
import com.wms.master.application.command.UpdateWarehouseCommand;
import com.wms.master.application.query.ListWarehousesQuery;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.event.WarehouseCreatedEvent;
import com.wms.master.domain.event.WarehouseDeactivatedEvent;
import com.wms.master.domain.event.WarehouseReactivatedEvent;
import com.wms.master.domain.event.WarehouseUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ReferenceIntegrityViolationException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class WarehouseServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    private FakeWarehousePersistencePort persistence;
    private FakeDomainEventPort events;
    private WarehouseService service;

    @BeforeEach
    void setUp() {
        persistence = new FakeWarehousePersistencePort();
        events = new FakeDomainEventPort();
        service = new WarehouseService(persistence, events);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists a new warehouse at version 0 and publishes Created event")
        void happyPath() {
            WarehouseResult result = service.create(new CreateWarehouseCommand(
                    "WH01", "Seoul Main", "Seoul, Korea", "Asia/Seoul", ACTOR));

            assertThat(result.id()).isNotNull();
            assertThat(result.warehouseCode()).isEqualTo("WH01");
            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isZero();
            assertThat(result.createdBy()).isEqualTo(ACTOR);

            WarehouseCreatedEvent event = events.single(WarehouseCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
            assertThat(event.actorId()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("rejects duplicate warehouseCode from the port")
        void duplicateCode() {
            service.create(new CreateWarehouseCommand(
                    "WH01", "First", null, "UTC", ACTOR));

            assertThatThrownBy(() -> service.create(new CreateWarehouseCommand(
                    "WH01", "Second", null, "UTC", ACTOR)))
                    .isInstanceOf(WarehouseCodeDuplicateException.class)
                    .hasMessageContaining("WH01");

            assertThat(events.published()).hasSize(1);
        }

        @Test
        @DisplayName("domain validation failures bubble up unchanged")
        void validationFailure() {
            assertThatThrownBy(() -> service.create(new CreateWarehouseCommand(
                    "bad-code", "Name", null, "UTC", ACTOR)))
                    .isInstanceOf(ValidationException.class);
            assertThat(persistence.size()).isZero();
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mutable fields change, version arg matches stored, event lists changed fields")
        void updatesMutableFields() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Original", "Original Addr", "UTC", ACTOR));
            events.clear();

            WarehouseResult updated = service.update(new UpdateWarehouseCommand(
                    created.id(),
                    "Renamed",
                    null,
                    "Asia/Seoul",
                    created.version(),
                    ACTOR_2));

            assertThat(updated.name()).isEqualTo("Renamed");
            assertThat(updated.address()).isEqualTo("Original Addr");
            assertThat(updated.timezone()).isEqualTo("Asia/Seoul");
            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(updated.updatedBy()).isEqualTo(ACTOR_2);

            WarehouseUpdatedEvent event = events.single(WarehouseUpdatedEvent.class);
            assertThat(event.changedFields()).containsExactlyInAnyOrder("name", "timezone");
        }

        @Test
        @DisplayName("no-op update still saves but emits no event")
        void noChangesNoEvent() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH02", "Same", "Same Addr", "UTC", ACTOR));
            events.clear();

            WarehouseResult updated = service.update(new UpdateWarehouseCommand(
                    created.id(),
                    "Same",
                    "Same Addr",
                    "UTC",
                    created.version(),
                    ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("unknown id yields WarehouseNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdateWarehouseCommand(
                    UUID.randomUUID(), "n", null, null, 0L, ACTOR)))
                    .isInstanceOf(WarehouseNotFoundException.class);
        }

        @Test
        @DisplayName("stale caller version yields ConcurrencyConflictException before save")
        void staleVersionConflict() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH03", "Name", null, "UTC", ACTOR));
            service.update(new UpdateWarehouseCommand(
                    created.id(), "Name-2", null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateWarehouseCommand(
                    created.id(), "Will Fail", null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class)
                    .hasMessageContaining("version 0")
                    .hasMessageContaining("current version 1");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLockTranslated() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH04", "Name", null, "UTC", ACTOR));
            events.clear();
            persistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdateWarehouseCommand(
                    created.id(), "Renamed", null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("invalid new timezone surfaces ValidationException, no save, no event")
        void invalidTimezone() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH05", "Name", null, "UTC", ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateWarehouseCommand(
                    created.id(), null, null, "Not/A_Zone", created.version(), ACTOR)))
                    .isInstanceOf(ValidationException.class);

            assertThat(persistence.stored(created.id()).getTimezone()).isEqualTo("UTC");
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("active warehouse becomes inactive, version bumps, event carries reason")
        void happyPath() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            events.clear();

            WarehouseResult result = service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "Closing", created.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(result.version()).isEqualTo(created.version() + 1);
            assertThat(result.updatedBy()).isEqualTo(ACTOR_2);

            WarehouseDeactivatedEvent event = events.single(WarehouseDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("Closing");
        }

        @Test
        @DisplayName("blocked when warehouse has active zones raises ReferenceIntegrityViolationException (REFERENCE_INTEGRITY_VIOLATION)")
        void deactivate_blockedByActiveZones() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            events.clear();
            persistence.markWarehouseAsHavingActiveZones(created.id());

            assertThatThrownBy(() -> service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "Closing", created.version(), ACTOR)))
                    .isInstanceOf(ReferenceIntegrityViolationException.class)
                    .hasMessageContaining("warehouse has active zones")
                    .extracting("code").isEqualTo("REFERENCE_INTEGRITY_VIOLATION");

            assertThat(events.published()).isEmpty();
            // The aggregate must remain ACTIVE — the guard runs BEFORE deactivate()
            // and BEFORE persistence.update(), so no version bump should have occurred.
            assertThat(persistence.stored(created.id()).getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(persistence.stored(created.id()).getVersion()).isEqualTo(created.version());
        }

        @Test
        @DisplayName("deactivating an already-inactive warehouse raises InvalidStateTransitionException")
        void alreadyInactive() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            WarehouseResult deactivated = service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "first", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "second", deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("stale version raises ConcurrencyConflictException")
        void staleVersion() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            service.update(new UpdateWarehouseCommand(
                    created.id(), "Renamed", null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "reason", created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("inactive warehouse becomes active and Reactivated event published")
        void happyPath() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            WarehouseResult deactivated = service.deactivate(new DeactivateWarehouseCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            WarehouseResult result = service.reactivate(new ReactivateWarehouseCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isEqualTo(deactivated.version() + 1);
            assertThat(events.single(WarehouseReactivatedEvent.class).aggregateId())
                    .isEqualTo(created.id());
        }

        @Test
        @DisplayName("reactivating an active warehouse raises InvalidStateTransitionException")
        void alreadyActive() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivateWarehouseCommand(
                    created.id(), created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted warehouse")
        void findByIdHit() {
            WarehouseResult created = service.create(new CreateWarehouseCommand(
                    "WH01", "Name", null, "UTC", ACTOR));

            WarehouseResult found = service.findById(created.id());

            assertThat(found.id()).isEqualTo(created.id());
            assertThat(found.warehouseCode()).isEqualTo("WH01");
        }

        @Test
        @DisplayName("findById on unknown id raises WarehouseNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(WarehouseNotFoundException.class);
        }

        @Test
        @DisplayName("findByCode on unknown code raises WarehouseNotFoundException")
        void findByCodeMiss() {
            assertThatThrownBy(() -> service.findByCode("WH99"))
                    .isInstanceOf(WarehouseNotFoundException.class);
        }

        @Test
        @DisplayName("list filters by status and paginates")
        void listFiltersAndPaginates() {
            WarehouseResult a = service.create(new CreateWarehouseCommand(
                    "WH10", "Alpha", null, "UTC", ACTOR));
            service.create(new CreateWarehouseCommand(
                    "WH11", "Bravo", null, "UTC", ACTOR));
            service.create(new CreateWarehouseCommand(
                    "WH12", "Charlie", null, "UTC", ACTOR));
            service.deactivate(new DeactivateWarehouseCommand(
                    a.id(), "closing", a.version(), ACTOR));

            PageResult<WarehouseResult> active = service.list(new ListWarehousesQuery(
                    new WarehouseListCriteria(WarehouseStatus.ACTIVE, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));

            assertThat(active.totalElements()).isEqualTo(2);
            assertThat(active.content())
                    .extracting(WarehouseResult::warehouseCode)
                    .containsExactlyInAnyOrder("WH11", "WH12");

            PageResult<WarehouseResult> byName = service.list(new ListWarehousesQuery(
                    new WarehouseListCriteria(null, "bravo"),
                    new PageQuery(0, 10, "updatedAt", "desc")));

            assertThat(byName.totalElements()).isEqualTo(1);
            assertThat(byName.content().get(0).warehouseCode()).isEqualTo("WH11");
        }
    }
}
