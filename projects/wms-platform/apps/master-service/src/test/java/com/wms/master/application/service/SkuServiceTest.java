package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateSkuCommand;
import com.wms.master.application.command.DeactivateSkuCommand;
import com.wms.master.application.command.ReactivateSkuCommand;
import com.wms.master.application.command.UpdateSkuCommand;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.application.query.ListSkusQuery;
import com.wms.master.application.result.SkuResult;
import com.wms.master.domain.event.SkuCreatedEvent;
import com.wms.master.domain.event.SkuDeactivatedEvent;
import com.wms.master.domain.event.SkuReactivatedEvent;
import com.wms.master.domain.event.SkuUpdatedEvent;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.TrackingType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkuServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    private FakeSkuPersistencePort persistence;
    private FakeDomainEventPort events;
    private SkuService service;

    @BeforeEach
    void setUp() {
        persistence = new FakeSkuPersistencePort();
        events = new FakeDomainEventPort();
        service = new SkuService(persistence, events);
    }

    private static CreateSkuCommand baseCreate(String skuCode) {
        return new CreateSkuCommand(skuCode, "Name", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists a new SKU, stores UPPERCASE skuCode, publishes Created event")
        void happyPath() {
            SkuResult result = service.create(new CreateSkuCommand(
                    "sku-apple-001", "Gala Apple", "fresh", "BC-1",
                    BaseUom.EA, TrackingType.LOT, 1000, null, null, 30, ACTOR));

            assertThat(result.id()).isNotNull();
            assertThat(result.skuCode()).isEqualTo("SKU-APPLE-001");
            assertThat(result.trackingType()).isEqualTo(TrackingType.LOT);
            assertThat(result.baseUom()).isEqualTo(BaseUom.EA);
            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isZero();

            SkuCreatedEvent event = events.single(SkuCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
            assertThat(event.snapshot().getSkuCode()).isEqualTo("SKU-APPLE-001");
        }

        @Test
        @DisplayName("case-insensitive duplicate rejected")
        void caseInsensitiveDuplicate() {
            service.create(baseCreate("sku-001"));
            events.clear();

            assertThatThrownBy(() -> service.create(baseCreate("SKU-001")))
                    .isInstanceOf(SkuCodeDuplicateException.class)
                    .hasMessageContaining("SKU-001");

            assertThat(events.published()).isEmpty();
            assertThat(persistence.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("duplicate barcode rejected")
        void duplicateBarcode() {
            service.create(new CreateSkuCommand("SKU-1", "N", null, "BC-1",
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.create(new CreateSkuCommand(
                    "SKU-2", "N", null, "BC-1",
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR)))
                    .isInstanceOf(BarcodeDuplicateException.class);
        }

        @Test
        @DisplayName("multiple SKUs with null barcode coexist")
        void twoNullBarcodesCoexist() {
            service.create(baseCreate("SKU-A"));
            service.create(baseCreate("SKU-B"));
            assertThat(persistence.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("domain validation failures bubble up and nothing persists")
        void validationFailure() {
            assertThatThrownBy(() -> service.create(new CreateSkuCommand(
                    "", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR)))
                    .isInstanceOf(ValidationException.class);
            assertThat(persistence.size()).isZero();
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mutable fields change, version bumps, event lists changed fields")
        void updatesMutableFields() {
            SkuResult created = service.create(new CreateSkuCommand(
                    "SKU-1", "Original", "orig desc", "BC-1",
                    BaseUom.EA, TrackingType.NONE, 100, null, null, null, ACTOR));
            events.clear();

            SkuResult updated = service.update(new UpdateSkuCommand(
                    created.id(),
                    "Renamed", null, "BC-2", 200, null, "HAZ-X", 7,
                    null, null, null,
                    created.version(), ACTOR_2));

            assertThat(updated.name()).isEqualTo("Renamed");
            assertThat(updated.description()).isEqualTo("orig desc");
            assertThat(updated.barcode()).isEqualTo("BC-2");
            assertThat(updated.weightGrams()).isEqualTo(200);
            assertThat(updated.hazardClass()).isEqualTo("HAZ-X");
            assertThat(updated.shelfLifeDays()).isEqualTo(7);
            assertThat(updated.version()).isEqualTo(created.version() + 1);

            SkuUpdatedEvent event = events.single(SkuUpdatedEvent.class);
            assertThat(event.changedFields())
                    .containsExactlyInAnyOrder("name", "barcode", "weightGrams", "hazardClass", "shelfLifeDays");
        }

        @Test
        @DisplayName("PATCH with skuCode change raises ImmutableFieldException")
        void patchSkuCodeRejected() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), null, null, null, null, null, null, null,
                    "SKU-99", null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("skuCode");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("PATCH with baseUom change raises ImmutableFieldException")
        void patchBaseUomRejected() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), null, null, null, null, null, null, null,
                    null, BaseUom.KG, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("baseUom");
        }

        @Test
        @DisplayName("PATCH with trackingType change raises ImmutableFieldException")
        void patchTrackingTypeRejected() {
            SkuResult created = service.create(baseCreate("SKU-1"));

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), null, null, null, null, null, null, null,
                    null, null, TrackingType.LOT,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("trackingType");
        }

        @Test
        @DisplayName("immutable-field check runs before version check")
        void immutableBeforeVersion() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();
            long wrongVersion = created.version() + 999;

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), null, null, null, null, null, null, null,
                    "SKU-99", null, null,
                    wrongVersion, ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class);
        }

        @Test
        @DisplayName("no-op update still saves but emits no event")
        void noChangesNoEvent() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();

            SkuResult updated = service.update(new UpdateSkuCommand(
                    created.id(),
                    created.name(), null, null, null, null, null, null,
                    null, null, null,
                    created.version(), ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("unknown id yields SkuNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    UUID.randomUUID(), "n", null, null, null, null, null, null,
                    null, null, null,
                    0L, ACTOR)))
                    .isInstanceOf(SkuNotFoundException.class);
        }

        @Test
        @DisplayName("stale caller version yields ConcurrencyConflictException")
        void staleVersionConflict() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            service.update(new UpdateSkuCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null, null, null,
                    created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), "Later", null, null, null, null, null, null,
                    null, null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLockTranslated() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();
            persistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdateSkuCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null, null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("happy path: no active lots, status -> INACTIVE, event carries reason")
        void happyPath() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();
            persistence.setHasActiveLots(false);

            SkuResult result = service.deactivate(new DeactivateSkuCommand(
                    created.id(), "discontinued", created.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(result.version()).isEqualTo(created.version() + 1);
            assertThat(result.updatedBy()).isEqualTo(ACTOR_2);

            SkuDeactivatedEvent event = events.single(SkuDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("discontinued");
        }

        @Test
        @DisplayName("blocked when hasActiveLotsFor=true, no state change, no event")
        void blockedByActiveLots() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();
            persistence.setHasActiveLots(true);

            assertThatThrownBy(() -> service.deactivate(new DeactivateSkuCommand(
                    created.id(), "closing", created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("active lots");

            assertThat(events.published()).isEmpty();
            assertThat(persistence.stored(created.id()).getStatus())
                    .isEqualTo(WarehouseStatus.ACTIVE);
        }

        @Test
        @DisplayName("double deactivate raises InvalidStateTransitionException")
        void alreadyInactive() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            SkuResult deactivated = service.deactivate(new DeactivateSkuCommand(
                    created.id(), "first", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateSkuCommand(
                    created.id(), "second", deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("stale version raises ConcurrencyConflictException")
        void staleVersion() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            service.update(new UpdateSkuCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null, null, null,
                    created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateSkuCommand(
                    created.id(), "reason", created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("inactive SKU becomes active; event published")
        void happyPath() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            SkuResult deactivated = service.deactivate(new DeactivateSkuCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            SkuResult result = service.reactivate(new ReactivateSkuCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isEqualTo(deactivated.version() + 1);
            assertThat(events.single(SkuReactivatedEvent.class).aggregateId())
                    .isEqualTo(created.id());
        }

        @Test
        @DisplayName("reactivate on ACTIVE raises InvalidStateTransitionException")
        void alreadyActive() {
            SkuResult created = service.create(baseCreate("SKU-1"));
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivateSkuCommand(
                    created.id(), created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted SKU")
        void findByIdHit() {
            SkuResult created = service.create(baseCreate("SKU-1"));

            SkuResult found = service.findById(created.id());
            assertThat(found.id()).isEqualTo(created.id());
        }

        @Test
        @DisplayName("findById unknown -> SkuNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(SkuNotFoundException.class);
        }

        @Test
        @DisplayName("findBySkuCode normalizes input casing")
        void findBySkuCodeCaseInsensitive() {
            service.create(baseCreate("SKU-APPLE-001"));

            SkuResult upper = service.findBySkuCode("SKU-APPLE-001");
            SkuResult lower = service.findBySkuCode("sku-apple-001");
            SkuResult mixed = service.findBySkuCode("Sku-Apple-001");

            assertThat(upper.id()).isEqualTo(lower.id()).isEqualTo(mixed.id());
        }

        @Test
        @DisplayName("findBySkuCode miss -> SkuNotFoundException")
        void findBySkuCodeMiss() {
            assertThatThrownBy(() -> service.findBySkuCode("SKU-NOPE"))
                    .isInstanceOf(SkuNotFoundException.class);
        }

        @Test
        @DisplayName("findByBarcode hit and miss")
        void findByBarcode() {
            service.create(new CreateSkuCommand(
                    "SKU-1", "N", null, "BC-HIT",
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR));
            SkuResult found = service.findByBarcode("BC-HIT");
            assertThat(found.skuCode()).isEqualTo("SKU-1");

            assertThatThrownBy(() -> service.findByBarcode("BC-NOPE"))
                    .isInstanceOf(SkuNotFoundException.class);
        }

        @Test
        @DisplayName("list filters by status, trackingType, baseUom, barcode and paginates")
        void listFiltersAndPaginates() {
            SkuResult a = service.create(new CreateSkuCommand(
                    "SKU-A", "Alpha", null, "BC-A",
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR));
            service.create(new CreateSkuCommand(
                    "SKU-B", "Bravo", null, null,
                    BaseUom.BOX, TrackingType.LOT, null, null, null, null, ACTOR));
            service.create(new CreateSkuCommand(
                    "SKU-C", "Charlie", null, null,
                    BaseUom.KG, TrackingType.NONE, null, null, null, null, ACTOR));
            service.deactivate(new DeactivateSkuCommand(a.id(), "r", a.version(), ACTOR));

            PageResult<SkuResult> active = service.list(new ListSkusQuery(
                    new ListSkusCriteria(WarehouseStatus.ACTIVE, null, null, null, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(active.totalElements()).isEqualTo(2);

            PageResult<SkuResult> lot = service.list(new ListSkusQuery(
                    new ListSkusCriteria(null, null, TrackingType.LOT, null, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(lot.totalElements()).isEqualTo(1);

            PageResult<SkuResult> byBarcode = service.list(new ListSkusQuery(
                    new ListSkusCriteria(null, null, null, null, "BC-A"),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(byBarcode.totalElements()).isEqualTo(1);

            PageResult<SkuResult> byQ = service.list(new ListSkusQuery(
                    new ListSkusCriteria(null, "bravo", null, null, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(byQ.totalElements()).isEqualTo(1);
            assertThat(byQ.content().get(0).skuCode()).isEqualTo("SKU-B");
        }
    }
}
