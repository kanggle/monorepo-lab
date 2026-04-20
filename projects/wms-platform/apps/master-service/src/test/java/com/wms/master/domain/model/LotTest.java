package com.wms.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LotTest {

    private static final UUID SKU_ID = UUID.randomUUID();
    private static final UUID SUPPLIER_ID = UUID.randomUUID();
    private static final String ACTOR = "actor-1";
    private static final String ACTOR_2 = "actor-2";

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("happy path: ACTIVE, version 0, trimmed lot_no, both dates set")
        void happyPath() {
            Lot lot = Lot.create(SKU_ID, "  L-20260418-A  ",
                    LocalDate.of(2026, 4, 15), LocalDate.of(2026, 5, 15),
                    SUPPLIER_ID, ACTOR);

            assertThat(lot.getId()).isNotNull();
            assertThat(lot.getSkuId()).isEqualTo(SKU_ID);
            assertThat(lot.getLotNo()).isEqualTo("L-20260418-A");
            assertThat(lot.getManufacturedDate()).isEqualTo(LocalDate.of(2026, 4, 15));
            assertThat(lot.getExpiryDate()).isEqualTo(LocalDate.of(2026, 5, 15));
            assertThat(lot.getSupplierPartnerId()).isEqualTo(SUPPLIER_ID);
            assertThat(lot.getStatus()).isEqualTo(LotStatus.ACTIVE);
            assertThat(lot.getVersion()).isZero();
            assertThat(lot.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(lot.isActive()).isTrue();
        }

        @Test
        @DisplayName("both dates null is permitted")
        void bothDatesNull() {
            Lot lot = Lot.create(SKU_ID, "L-1", null, null, null, ACTOR);
            assertThat(lot.getManufacturedDate()).isNull();
            assertThat(lot.getExpiryDate()).isNull();
            assertThat(lot.getSupplierPartnerId()).isNull();
        }

        @Test
        @DisplayName("expiry < manufactured raises ValidationException")
        void invalidDatePair() {
            assertThatThrownBy(() -> Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 5, 15), LocalDate.of(2026, 4, 15),
                    null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("expiryDate");
        }

        @Test
        @DisplayName("same-day expiry and manufactured is permitted")
        void sameDayDatePair() {
            LocalDate today = LocalDate.of(2026, 4, 20);
            Lot lot = Lot.create(SKU_ID, "L-same", today, today, null, ACTOR);
            assertThat(lot.getExpiryDate()).isEqualTo(lot.getManufacturedDate());
        }

        @Test
        @DisplayName("null skuId raises ValidationException")
        void missingSkuId() {
            assertThatThrownBy(() -> Lot.create(null, "L-1", null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("skuId");
        }

        @Test
        @DisplayName("blank lotNo raises ValidationException")
        void blankLotNo() {
            assertThatThrownBy(() -> Lot.create(SKU_ID, "   ", null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("lotNo");
        }

        @Test
        @DisplayName("lotNo over 40 chars raises ValidationException")
        void oversizedLotNo() {
            String oversized = "L-" + "x".repeat(40);
            assertThatThrownBy(() -> Lot.create(SKU_ID, oversized, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("40");
        }

        @Test
        @DisplayName("blank actorId raises ValidationException")
        void missingActor() {
            assertThatThrownBy(() -> Lot.create(SKU_ID, "L-1", null, null, null, ""))
                    .isInstanceOf(ValidationException.class);
        }
    }

    @Nested
    @DisplayName("state machine")
    class StateMachine {

        @Test
        @DisplayName("deactivate ACTIVE → INACTIVE")
        void deactivateFromActive() {
            Lot lot = baseActive();
            lot.deactivate(ACTOR_2);
            assertThat(lot.getStatus()).isEqualTo(LotStatus.INACTIVE);
            assertThat(lot.getUpdatedBy()).isEqualTo(ACTOR_2);
        }

        @Test
        @DisplayName("deactivate EXPIRED → INACTIVE (allowed per domain-model.md §6)")
        void deactivateFromExpired() {
            Lot lot = baseActive();
            lot.expire(ACTOR);
            lot.deactivate(ACTOR);
            assertThat(lot.getStatus()).isEqualTo(LotStatus.INACTIVE);
        }

        @Test
        @DisplayName("deactivate already-INACTIVE raises InvalidStateTransitionException")
        void deactivateFromInactive() {
            Lot lot = baseActive();
            lot.deactivate(ACTOR);
            assertThatThrownBy(() -> lot.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate INACTIVE → ACTIVE")
        void reactivateFromInactive() {
            Lot lot = baseActive();
            lot.deactivate(ACTOR);
            lot.reactivate(ACTOR_2);
            assertThat(lot.getStatus()).isEqualTo(LotStatus.ACTIVE);
            assertThat(lot.getUpdatedBy()).isEqualTo(ACTOR_2);
        }

        @Test
        @DisplayName("reactivate ACTIVE raises InvalidStateTransitionException")
        void reactivateFromActive() {
            Lot lot = baseActive();
            assertThatThrownBy(() -> lot.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate EXPIRED is blocked (EXPIRED is terminal for reactivation)")
        void reactivateFromExpiredBlocked() {
            Lot lot = baseActive();
            lot.expire(ACTOR);
            assertThatThrownBy(() -> lot.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("expire ACTIVE → EXPIRED")
        void expireFromActive() {
            Lot lot = baseActive();
            lot.expire(ACTOR);
            assertThat(lot.getStatus()).isEqualTo(LotStatus.EXPIRED);
            assertThat(lot.isExpired()).isTrue();
        }

        @Test
        @DisplayName("expire INACTIVE raises InvalidStateTransitionException")
        void expireFromInactive() {
            Lot lot = baseActive();
            lot.deactivate(ACTOR);
            assertThatThrownBy(() -> lot.expire(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("expire EXPIRED raises InvalidStateTransitionException")
        void expireFromExpired() {
            Lot lot = baseActive();
            lot.expire(ACTOR);
            assertThatThrownBy(() -> lot.expire(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("applyUpdate + rejectImmutableChange")
    class Updates {

        @Test
        @DisplayName("applyUpdate mutates expiryDate + supplierPartnerId only")
        void applyUpdateHappy() {
            Lot lot = Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), null, ACTOR);
            UUID newSupplier = UUID.randomUUID();
            lot.applyUpdate(LocalDate.of(2026, 6, 1), newSupplier, false, ACTOR_2);

            assertThat(lot.getExpiryDate()).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(lot.getSupplierPartnerId()).isEqualTo(newSupplier);
            // immutable fields preserved
            assertThat(lot.getSkuId()).isEqualTo(SKU_ID);
            assertThat(lot.getLotNo()).isEqualTo("L-1");
            assertThat(lot.getManufacturedDate()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(lot.getUpdatedBy()).isEqualTo(ACTOR_2);
        }

        @Test
        @DisplayName("applyUpdate with null args leaves fields unchanged")
        void applyUpdateNoop() {
            Lot lot = Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1), SUPPLIER_ID, ACTOR);
            lot.applyUpdate(null, null, false, ACTOR);

            assertThat(lot.getExpiryDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(lot.getSupplierPartnerId()).isEqualTo(SUPPLIER_ID);
        }

        @Test
        @DisplayName("clearSupplierPartnerId=true nulls the link even if supplierPartnerId arg is null")
        void clearSupplierPartnerId() {
            Lot lot = Lot.create(SKU_ID, "L-1", null, null, SUPPLIER_ID, ACTOR);
            lot.applyUpdate(null, null, true, ACTOR);
            assertThat(lot.getSupplierPartnerId()).isNull();
        }

        @Test
        @DisplayName("applyUpdate with new expiry < current manufactured raises ValidationException")
        void applyUpdateInvalidDatePair() {
            Lot lot = Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), null, ACTOR);
            assertThatThrownBy(() -> lot.applyUpdate(
                    LocalDate.of(2026, 4, 1), null, false, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange on different skuId raises ImmutableFieldException")
        void rejectSkuIdChange() {
            Lot lot = Lot.create(SKU_ID, "L-1", null, null, null, ACTOR);
            assertThatThrownBy(() -> lot.rejectImmutableChange(UUID.randomUUID(), null, null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("skuId");
        }

        @Test
        @DisplayName("rejectImmutableChange on different lotNo raises ImmutableFieldException")
        void rejectLotNoChange() {
            Lot lot = Lot.create(SKU_ID, "L-1", null, null, null, ACTOR);
            assertThatThrownBy(() -> lot.rejectImmutableChange(null, "L-2", null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("lotNo");
        }

        @Test
        @DisplayName("rejectImmutableChange on different manufacturedDate raises ImmutableFieldException")
        void rejectManufacturedDateChange() {
            Lot lot = Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 4, 1), null, null, ACTOR);
            assertThatThrownBy(() -> lot.rejectImmutableChange(
                    null, null, LocalDate.of(2026, 4, 2)))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("manufacturedDate");
        }

        @Test
        @DisplayName("rejectImmutableChange with identical values is a no-op (not an error)")
        void rejectIdentical() {
            Lot lot = Lot.create(SKU_ID, "L-1",
                    LocalDate.of(2026, 4, 1), null, null, ACTOR);
            // Must not throw.
            lot.rejectImmutableChange(SKU_ID, "L-1", LocalDate.of(2026, 4, 1));
        }
    }

    private static Lot baseActive() {
        return Lot.create(SKU_ID, "L-base",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
                null, ACTOR);
    }
}
