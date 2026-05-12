package com.wms.master.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.command.CreatePartnerCommand;
import com.wms.master.application.command.DeactivatePartnerCommand;
import com.wms.master.application.command.ReactivatePartnerCommand;
import com.wms.master.application.command.UpdatePartnerCommand;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.application.query.ListPartnersQuery;
import com.wms.master.application.result.PartnerResult;
import com.wms.master.domain.event.PartnerCreatedEvent;
import com.wms.master.domain.event.PartnerDeactivatedEvent;
import com.wms.master.domain.event.PartnerReactivatedEvent;
import com.wms.master.domain.event.PartnerUpdatedEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.exception.PartnerNotFoundException;
import com.wms.master.domain.exception.ValidationException;
import com.wms.master.domain.model.PartnerType;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PartnerServiceTest {

    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    private FakePartnerPersistencePort persistence;
    private FakeDomainEventPort events;
    private PartnerService service;

    @BeforeEach
    void setUp() {
        persistence = new FakePartnerPersistencePort();
        events = new FakeDomainEventPort();
        service = new PartnerService(persistence, events);
    }

    private static CreatePartnerCommand baseCreate(String code, PartnerType type) {
        return new CreatePartnerCommand(code, "Name", type,
                null, null, null, null, null, ACTOR);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("persists a new Partner and publishes Created event")
        void happyPath() {
            PartnerResult result = service.create(new CreatePartnerCommand(
                    "SUP-001", "ACME", PartnerType.SUPPLIER,
                    "B-1", "Jane", "j@example.com", "+82-1", "Seoul", ACTOR));

            assertThat(result.id()).isNotNull();
            assertThat(result.partnerCode()).isEqualTo("SUP-001");
            assertThat(result.partnerType()).isEqualTo(PartnerType.SUPPLIER);
            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isZero();

            PartnerCreatedEvent event = events.single(PartnerCreatedEvent.class);
            assertThat(event.aggregateId()).isEqualTo(result.id());
            assertThat(event.snapshot().getPartnerCode()).isEqualTo("SUP-001");
        }

        @Test
        @DisplayName("duplicate partnerCode rejected")
        void duplicateCode() {
            service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();

            assertThatThrownBy(() -> service.create(baseCreate("SUP-1", PartnerType.CUSTOMER)))
                    .isInstanceOf(PartnerCodeDuplicateException.class)
                    .hasMessageContaining("SUP-1");

            assertThat(events.published()).isEmpty();
            assertThat(persistence.size()).isEqualTo(1);
        }

        @Test
        @DisplayName("domain validation failures bubble up and nothing persists")
        void validationFailure() {
            assertThatThrownBy(() -> service.create(new CreatePartnerCommand(
                    "", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR)))
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
            PartnerResult created = service.create(new CreatePartnerCommand(
                    "SUP-1", "Original", PartnerType.SUPPLIER,
                    "B-1", "C-Name", "c@example.com", "+82-1", "Seoul", ACTOR));
            events.clear();

            PartnerResult updated = service.update(new UpdatePartnerCommand(
                    created.id(),
                    "Renamed", PartnerType.BOTH, "B-2", null,
                    "new@example.com", null, "Busan",
                    null,
                    created.version(), ACTOR_2));

            assertThat(updated.name()).isEqualTo("Renamed");
            assertThat(updated.partnerType()).isEqualTo(PartnerType.BOTH);
            assertThat(updated.businessNumber()).isEqualTo("B-2");
            assertThat(updated.contactName()).isEqualTo("C-Name");
            assertThat(updated.contactEmail()).isEqualTo("new@example.com");
            assertThat(updated.contactPhone()).isEqualTo("+82-1");
            assertThat(updated.address()).isEqualTo("Busan");
            assertThat(updated.version()).isEqualTo(created.version() + 1);

            PartnerUpdatedEvent event = events.single(PartnerUpdatedEvent.class);
            assertThat(event.changedFields())
                    .containsExactlyInAnyOrder("name", "partnerType", "businessNumber",
                            "contactEmail", "address");
        }

        @Test
        @DisplayName("PATCH with partnerCode change raises ImmutableFieldException")
        void patchPartnerCodeRejected() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdatePartnerCommand(
                    created.id(), null, null, null, null, null, null, null,
                    "SUP-99",
                    created.version(), ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("partnerCode");

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("immutable-field check runs before version check")
        void immutableBeforeVersion() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();
            long wrongVersion = created.version() + 999;

            assertThatThrownBy(() -> service.update(new UpdatePartnerCommand(
                    created.id(), null, null, null, null, null, null, null,
                    "SUP-99",
                    wrongVersion, ACTOR)))
                    .isInstanceOf(ImmutableFieldException.class);
        }

        @Test
        @DisplayName("no-op update still saves but emits no event")
        void noChangesNoEvent() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();

            PartnerResult updated = service.update(new UpdatePartnerCommand(
                    created.id(),
                    created.name(), null, null, null, null, null, null,
                    null,
                    created.version(), ACTOR));

            assertThat(updated.version()).isEqualTo(created.version() + 1);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("unknown id yields PartnerNotFoundException")
        void unknownId() {
            assertThatThrownBy(() -> service.update(new UpdatePartnerCommand(
                    UUID.randomUUID(), "n", null, null, null, null, null, null,
                    null,
                    0L, ACTOR)))
                    .isInstanceOf(PartnerNotFoundException.class);
        }

        @Test
        @DisplayName("stale caller version yields ConcurrencyConflictException")
        void staleVersionConflict() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            service.update(new UpdatePartnerCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null,
                    created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.update(new UpdatePartnerCommand(
                    created.id(), "Later", null, null, null, null, null, null,
                    null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("adapter-level optimistic lock failure is translated")
        void adapterOptimisticLockTranslated() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();
            persistence.triggerOptimisticLockOnNextUpdate();

            assertThatThrownBy(() -> service.update(new UpdatePartnerCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null,
                    created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("happy path: v1 no cross-aggregate check, status -> INACTIVE, event carries reason")
        void happyPath() {
            // v1 does NOT block on Lot.supplier_partner_id (soft reference) — see
            // domain-model.md §5 "v1: no such link". Deactivate succeeds always.
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();

            PartnerResult result = service.deactivate(new DeactivatePartnerCommand(
                    created.id(), "discontinued", created.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(result.version()).isEqualTo(created.version() + 1);
            assertThat(result.updatedBy()).isEqualTo(ACTOR_2);

            PartnerDeactivatedEvent event = events.single(PartnerDeactivatedEvent.class);
            assertThat(event.reason()).isEqualTo("discontinued");
        }

        @Test
        @DisplayName("double deactivate raises InvalidStateTransitionException")
        void alreadyInactive() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            PartnerResult deactivated = service.deactivate(new DeactivatePartnerCommand(
                    created.id(), "first", created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivatePartnerCommand(
                    created.id(), "second", deactivated.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);

            assertThat(events.published()).isEmpty();
        }

        @Test
        @DisplayName("stale version raises ConcurrencyConflictException")
        void staleVersion() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            service.update(new UpdatePartnerCommand(
                    created.id(), "Renamed", null, null, null, null, null, null,
                    null, created.version(), ACTOR));
            events.clear();

            assertThatThrownBy(() -> service.deactivate(new DeactivatePartnerCommand(
                    created.id(), "reason", created.version(), ACTOR)))
                    .isInstanceOf(ConcurrencyConflictException.class);
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("inactive Partner becomes active; event published")
        void happyPath() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            PartnerResult deactivated = service.deactivate(new DeactivatePartnerCommand(
                    created.id(), "r", created.version(), ACTOR));
            events.clear();

            PartnerResult result = service.reactivate(new ReactivatePartnerCommand(
                    created.id(), deactivated.version(), ACTOR_2));

            assertThat(result.status()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(result.version()).isEqualTo(deactivated.version() + 1);
            assertThat(events.single(PartnerReactivatedEvent.class).aggregateId())
                    .isEqualTo(created.id());
        }

        @Test
        @DisplayName("reactivate on ACTIVE raises InvalidStateTransitionException")
        void alreadyActive() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));
            events.clear();

            assertThatThrownBy(() -> service.reactivate(new ReactivatePartnerCommand(
                    created.id(), created.version(), ACTOR)))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("queries")
    class Queries {

        @Test
        @DisplayName("findById returns the persisted Partner")
        void findByIdHit() {
            PartnerResult created = service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));

            PartnerResult found = service.findById(created.id());
            assertThat(found.id()).isEqualTo(created.id());
        }

        @Test
        @DisplayName("findById unknown -> PartnerNotFoundException")
        void findByIdMiss() {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
                    .isInstanceOf(PartnerNotFoundException.class);
        }

        @Test
        @DisplayName("findByCode hit and miss")
        void findByCode() {
            service.create(baseCreate("SUP-1", PartnerType.SUPPLIER));

            PartnerResult hit = service.findByCode("SUP-1");
            assertThat(hit.partnerCode()).isEqualTo("SUP-1");

            assertThatThrownBy(() -> service.findByCode("NOPE"))
                    .isInstanceOf(PartnerNotFoundException.class);
        }

        @Test
        @DisplayName("list filters by status, partnerType, q text and paginates")
        void listFiltersAndPaginates() {
            PartnerResult a = service.create(baseCreate("SUP-A", PartnerType.SUPPLIER));
            service.create(baseCreate("CUST-B", PartnerType.CUSTOMER));
            service.create(baseCreate("BOTH-C", PartnerType.BOTH));
            service.deactivate(new DeactivatePartnerCommand(a.id(), "r", a.version(), ACTOR));

            PageResult<PartnerResult> active = service.list(new ListPartnersQuery(
                    new ListPartnersCriteria(WarehouseStatus.ACTIVE, null, null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(active.totalElements()).isEqualTo(2);

            PageResult<PartnerResult> supplierOnly = service.list(new ListPartnersQuery(
                    new ListPartnersCriteria(null, null, PartnerType.SUPPLIER),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(supplierOnly.totalElements()).isEqualTo(1);
            assertThat(supplierOnly.content().get(0).partnerCode()).isEqualTo("SUP-A");

            PageResult<PartnerResult> byQ = service.list(new ListPartnersQuery(
                    new ListPartnersCriteria(null, "both", null),
                    new PageQuery(0, 10, "updatedAt", "desc")));
            assertThat(byQ.totalElements()).isEqualTo(1);
            assertThat(byQ.content().get(0).partnerCode()).isEqualTo("BOTH-C");
        }
    }
}
