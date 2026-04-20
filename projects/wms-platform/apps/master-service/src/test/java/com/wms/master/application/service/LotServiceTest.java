package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.command.CreateLotCommand;
import com.wms.master.application.command.DeactivateLotCommand;
import com.wms.master.application.command.ReactivateLotCommand;
import com.wms.master.application.command.UpdateLotCommand;
import com.wms.master.application.port.in.ExpireLotsBatchUseCase.LotExpirationResult;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import com.wms.master.domain.event.LotCreatedEvent;
import com.wms.master.domain.event.LotDeactivatedEvent;
import com.wms.master.domain.event.LotExpiredEvent;
import com.wms.master.domain.event.LotReactivatedEvent;
import com.wms.master.domain.event.LotUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.exception.LotNotFoundException;
import com.wms.master.domain.exception.SkuNotFoundException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.BaseUom;
import com.wms.master.domain.model.LotStatus;
import com.wms.master.domain.model.Sku;
import com.wms.master.domain.model.TrackingType;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LotServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    private FakeLotPersistencePort lotPersistence;
    private FakeSkuPersistencePort skuPersistence;
    private FakeDomainEventPort events;
    private LotService service;

    private Sku activeLotSku;
    private Sku inactiveLotSku;
    private Sku activeNoneSku;

    @BeforeEach
    void setUp() {
        lotPersistence = new FakeLotPersistencePort();
        skuPersistence = new FakeSkuPersistencePort();
        events = new FakeDomainEventPort();
        service = new LotService(lotPersistence, skuPersistence, events);

        // Seed 3 parent SKUs with different tracking/status combinations
        activeLotSku = skuPersistence.insert(Sku.create(
                "SKU-LOT-ACTIVE", "LOT active", null, null,
                BaseUom.EA, TrackingType.LOT, null, null, null, 30, ACTOR));
        Sku inactive = Sku.create("SKU-LOT-INACTIVE", "LOT inactive", null, null,
                BaseUom.EA, TrackingType.LOT, null, null, null, 30, ACTOR);
        inactive.deactivate(ACTOR);
        inactiveLotSku = skuPersistence.insert(inactive);
        activeNoneSku = skuPersistence.insert(Sku.create(
                "SKU-NONE-ACTIVE", "NONE active", null, null,
                BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR));
    }

    private CreateLotCommand baseCreate(UUID skuId, String lotNo) {
        return new CreateLotCommand(
                skuId, lotNo,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                null, ACTOR);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path: persists Lot, emits LotCreatedEvent")
        void happyPath() {
            LotResult result = service.create(baseCreate(activeLotSku.getId(), "L-1"));

            assertThat(result.skuId()).isEqualTo(activeLotSku.getId());
            assertThat(result.lotNo()).isEqualTo("L-1");
            assertThat(result.status()).isEqualTo(LotStatus.ACTIVE);
            assertThat(result.version()).isZero();

            LotCreatedEvent event = events.single(LotCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
        }

        @Test
        @DisplayName("parent SKU not found → SkuNotFoundException (404)")
        void skuNotFound() {
            UUID ghost = UUID.randomUUID();
            assertThatThrownBy(() -> service.create(baseCreate(ghost, "L-1")))
                    .isInstanceOf(SkuNotFoundException.class);
            assertThat(events.published()).isEmpty();
            assertThat(lotPersistence.size()).isZero();
        }

        @Test
        @DisplayName("parent SKU INACTIVE → InvalidStateTransitionException (422)")
        void parentInactive() {
            assertThatThrownBy(() -> service.create(baseCreate(inactiveLotSku.getId(), "L-1")))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("not ACTIVE");
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("parent SKU NONE-tracked → InvalidStateTransitionException (422)")
        void parentNoneTracked() {
            assertThatThrownBy(() -> service.create(baseCreate(activeNoneSku.getId(), "L-1")))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("LOT-tracked");
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("duplicate lotNo per SKU → LotNoDuplicateException (409)")
        void duplicateLotNo() {
            service.create(baseCreate(activeLotSku.getId(), "L-dup"));
            events.clear();

            assertThatThrownBy(() -> service.create(baseCreate(activeLotSku.getId(), "L-dup")))
                    .isInstanceOf(LotNoDuplicateException.class);

            assertThat(events.published()).isEmpty();
            assertThat(lotPersistence.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("same lotNo allowed across different SKUs (per-SKU uniqueness)")
        void sameLotNoDifferentSku() {
            Sku otherSku = skuPersistence.insert(Sku.create(
                    "SKU-OTHER", "Other", null, null,
                    BaseUom.EA, TrackingType.LOT, null, null, null, 30, ACTOR));

            service.create(baseCreate(activeLotSku.getId(), "L-shared"));
            service.create(baseCreate(otherSku.getId(), "L-shared"));

            assertThat(lotPersistence.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("invalid date pair surfaces ValidationException")
        void invalidDatePair() {
            CreateLotCommand bad = new CreateLotCommand(
                    activeLotSku.getId(), "L-1",
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1),
                    null, ACTOR);
            assertThatThrownBy(() -> service.create(bad))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("mutable fields change, version bumps, event lists changed fields")
        void updatesMutableFields() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            events.clear();
            UUID newSupplier = UUID.randomUUID();

            LotResult updated = service.update(new UpdateLotCommand(
                    created.id(),
                    LocalDate.of(2026, 6, 1),
                    newSupplier,
                    false,
                    null, null, null,
                    created.version(), ACTOR_2));

            assertThat(updated.expiryDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(updated.supplierPartnerId()).isEqualTo(newSupplier);
            assertThat(updated.version()).isEqualTo(created.version() + 1);

            LotUpdatedEvent event = events.single(LotUpdatedEvent.class);
            assertThat(event.changedFields())
                    .containsExactlyInAnyOrder("expiryDate", "supplierPartnerId");
        }

        @Test
        @DisplayName("PATCH with lotNo change raises ImmutableFieldException")
        void patchLotNoRejected() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(), null, null, false,
                    null, "L-99", null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("lotNo");
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("PATCH with skuId change raises ImmutableFieldException")
        void patchSkuIdRejected() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(), null, null, false,
                    UUID.randomUUID(), null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("skuId");
        }

        @Test
        @DisplayName("PATCH with manufacturedDate change raises ImmutableFieldException")
        void patchManufacturedDateRejected() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(), null, null, false,
                    null, null, LocalDate.of(2024, 1, 1),
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("manufacturedDate");
        }

        @Test
        @DisplayName("immutable-field check runs BEFORE version check")
        void immutableBeforeVersion() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            long wrongVersion = created.version() + 99;

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(), null, null, false,
                    null, "L-99", null,
                    wrongVersion, ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class);
        }

        @Test
        @DisplayName("clearSupplierPartnerId=true drops the link and emits the changedField")
        void clearSupplier() {
            CreateLotCommand cmd = new CreateLotCommand(
                    activeLotSku.getId(), "L-1",
                    null, null, UUID.randomUUID(), ACTOR);
            LotResult created = service.create(cmd);
            events.clear();

            LotResult updated = service.update(new UpdateLotCommand(
                    created.id(),
                    null, null, true,
                    null, null, null,
                    created.version(), ACTOR));

            assertThat(updated.supplierPartnerId()).isNull();
            LotUpdatedEvent event = events.single(LotUpdatedEvent.class);
            assertThat(event.changedFields()).containsExactly("supplierPartnerId");
        }

        @Test
        @DisplayName("no-op update still bumps version but emits no event")
        void noopSuppressesEvent() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            events.clear();

            LotResult updated = service.update(new UpdateLotCommand(
                    created.id(),
                    null, null, false,
                    null, null, null,
                    created.version(), ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("unknown id -> LotNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    UUID.randomUUID(),
                    null, null, false,
                    null, null, null,
                    0L, ACTOR)))
                    .isInstanceOf(LotNotFoundException.class);
        }

        @Test
        @DisplayName("stale version -> ConcurrencyConflictException")
        void staleVersion() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            LotResult after = service.update(new UpdateLotCommand(
                    created.id(), LocalDate.of(2026, 6, 1), null, false,
                    null, null, null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(), LocalDate.of(2026, 7, 1), null, false,
                    null, null, null, created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
            assertThat(after.version()).isNotEqualTo(created.version());
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLockTranslated() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            events.clear();
            lotPersistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdateLotCommand(
                    created.id(),
                    LocalDate.of(2026, 6, 1), null, false,
                    null, null, null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("deactivate / reactivate")
    class DeactivateReactivate {

        @Test
        @DisplayName("deactivate ACTIVE lot → INACTIVE; event carries reason")
        void deactivateActive() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            events.clear();

            LotResult result = service.deactivate(new DeactivateLotCommand(
                    created.id(), "damaged", created.version(), ACTOR));

            assertThat(result.status()).isEqualTo(LotStatus.INACTIVE);
            LotDeactivatedEvent event = events.single(LotDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("damaged");
        }

        @Test
        @DisplayName("double deactivate raises InvalidStateTransitionException")
        void doubleDeactivate() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            LotResult first = service.deactivate(new DeactivateLotCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivateLotCommand(
                    created.id(), "r2", first.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("reactivate from INACTIVE → ACTIVE")
        void reactivateFromInactive() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            LotResult deactivated = service.deactivate(new DeactivateLotCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            LotResult result = service.reactivate(new ReactivateLotCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(LotStatus.ACTIVE);
            events.single(LotReactivatedEvent.class);
        }

        @Test
        @DisplayName("reactivate EXPIRED is blocked (EXPIRED is terminal)")
        void reactivateFromExpiredBlocked() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            // Expire via the batch path to put the lot in EXPIRED.
            service.execute(LocalDate.of(2026, 6, 1)); // after expiry
            events.clear();

            // Reload to get the new version post-expiry.
            LotResult expired = service.findById(created.id());
            assertThat(expired.status()).isEqualTo(LotStatus.EXPIRED);

            assertThatThrownBy(() -> service.reactivate(new ReactivateLotCommand(
                    created.id(), expired.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("expireBatch")
    class ExpireBatch {

        @Test
        @DisplayName("expires only ACTIVE lots with expiry_date strictly before today")
        void happyPath() {
            LotResult willExpire = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-expire",
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1),
                    null, ACTOR));
            LotResult exactlyToday = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-today",
                    null, LocalDate.of(2026, 4, 20),
                    null, ACTOR));
            LotResult future = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-future",
                    null, LocalDate.of(2027, 1, 1),
                    null, ACTOR));
            LotResult noExpiry = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-permanent",
                    null, null, null, ACTOR));
            events.clear();

            LotExpirationResult result = service.execute(LocalDate.of(2026, 4, 20));

            assertThat(result.considered()).isEqualTo(1);
            assertThat(result.expired()).isEqualTo(1);
            assertThat(result.failed()).isZero();

            assertThat(service.findById(willExpire.id()).status()).isEqualTo(LotStatus.EXPIRED);
            assertThat(service.findById(exactlyToday.id()).status()).isEqualTo(LotStatus.ACTIVE);
            assertThat(service.findById(future.id()).status()).isEqualTo(LotStatus.ACTIVE);
            assertThat(service.findById(noExpiry.id()).status()).isEqualTo(LotStatus.ACTIVE);

            LotExpiredEvent event = events.single(LotExpiredEvent.class);
            assertThat(event.aggregateId()).isEqualTo(willExpire.id());
            assertThat(event.actorId()).isNull();
            assertThat(event.triggeredBy()).isEqualTo("scheduled-job:lot-expiry");
        }

        @Test
        @DisplayName("per-row failure does not abort the rest of the batch")
        void batchIsolatesFailure() {
            LotResult good1 = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-good1",
                    null, LocalDate.of(2026, 1, 1), null, ACTOR));
            LotResult bad = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-bad",
                    null, LocalDate.of(2026, 1, 1), null, ACTOR));
            LotResult good2 = service.create(new CreateLotCommand(
                    activeLotSku.getId(), "L-good2",
                    null, LocalDate.of(2026, 1, 1), null, ACTOR));
            events.clear();

            // Arrange: the adapter rejects the middle row's save with an
            // optimistic-lock failure. The batch should log and continue.
            lotPersistence.triggerOptimisticLockFor(bad.id());

            LotExpirationResult result = service.execute(LocalDate.of(2026, 4, 20));

            assertThat(result.considered()).isEqualTo(3);
            assertThat(result.expired()).isEqualTo(2);
            assertThat(result.failed()).isEqualTo(1);

            assertThat(service.findById(good1.id()).status()).isEqualTo(LotStatus.EXPIRED);
            assertThat(service.findById(bad.id()).status()).isEqualTo(LotStatus.ACTIVE);
            assertThat(service.findById(good2.id()).status()).isEqualTo(LotStatus.EXPIRED);

            assertThat(events.published()).hasSize(2);
        }

        @Test
        @DisplayName("empty batch produces no events and zero counts")
        void emptyBatch() {
            LotExpirationResult result = service.execute(LocalDate.of(2026, 4, 20));
            assertThat(result.considered()).isZero();
            assertThat(result.expired()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(events.published()).isEmpty();
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted Lot")
        void findByIdHit() {
            LotResult created = service.create(baseCreate(activeLotSku.getId(), "L-1"));
            LotResult found = service.findById(created.id());
            assertThat(found.id()).isEqualTo(created.id());
        }

        @Test
        @DisplayName("findById miss → LotNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(LotNotFoundException.class);
        }

        @Test
        @DisplayName("list filters by skuId and status")
        void listFilters() {
            service.create(baseCreate(activeLotSku.getId(), "L-a"));
            service.create(baseCreate(activeLotSku.getId(), "L-b"));

            PageResult<LotResult> page = service.list(new ListLotsQuery(
                    new ListLotsCriteria(activeLotSku.getId(), LotStatus.ACTIVE, null, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(page.totalElements()).isEqualTo(2);
        }
    }
}
