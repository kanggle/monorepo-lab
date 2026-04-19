package com.wms.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SkuTest {

    private static final String ACTOR = "actor-uuid";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid inputs produce an ACTIVE SKU at version 0")
        void createValid() {
            Sku sku = Sku.create(
                    "SKU-APPLE-001", "Gala Apple 1kg", "fresh", "8801234567890",
                    BaseUom.EA, TrackingType.LOT, 1000, null, null, 30, ACTOR);

            assertThat(sku.getSkuCode()).isEqualTo("SKU-APPLE-001");
            assertThat(sku.getName()).isEqualTo("Gala Apple 1kg");
            assertThat(sku.getDescription()).isEqualTo("fresh");
            assertThat(sku.getBarcode()).isEqualTo("8801234567890");
            assertThat(sku.getBaseUom()).isEqualTo(BaseUom.EA);
            assertThat(sku.getTrackingType()).isEqualTo(TrackingType.LOT);
            assertThat(sku.getWeightGrams()).isEqualTo(1000);
            assertThat(sku.getShelfLifeDays()).isEqualTo(30);
            assertThat(sku.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(sku.getVersion()).isZero();
            assertThat(sku.getId()).isNotNull();
            assertThat(sku.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(sku.getUpdatedBy()).isEqualTo(ACTOR);
            assertThat(sku.isActive()).isTrue();
        }

        @Test
        @DisplayName("skuCode is normalized to UPPERCASE")
        void normalizesSkuCode() {
            Sku sku = Sku.create(
                    "sku-apple-001", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThat(sku.getSkuCode()).isEqualTo("SKU-APPLE-001");
        }

        @Test
        @DisplayName("mixed-case skuCode normalizes consistently")
        void normalizesMixedCase() {
            Sku lower = Sku.create("sku-a", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            Sku upper = Sku.create("SKU-A", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            Sku mixed = Sku.create("Sku-A", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThat(lower.getSkuCode())
                    .isEqualTo(upper.getSkuCode())
                    .isEqualTo(mixed.getSkuCode())
                    .isEqualTo("SKU-A");
        }

        @Test
        @DisplayName("null or blank skuCode rejected")
        void rejectsBlankCode() {
            assertThatThrownBy(() -> Sku.create(null, "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("skuCode");
            assertThatThrownBy(() -> Sku.create("  ", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("skuCode over 40 chars rejected")
        void rejectsLongSkuCode() {
            String long41 = "x".repeat(41);
            assertThatThrownBy(() -> Sku.create(long41, "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank name rejected")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Sku.create("SKU-1", "", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("name over 200 chars rejected")
        void rejectsLongName() {
            String long201 = "x".repeat(201);
            assertThatThrownBy(() -> Sku.create("SKU-1", long201, null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("description over 1000 chars rejected")
        void rejectsLongDescription() {
            String long1001 = "x".repeat(1001);
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", long1001, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("barcode over 40 chars rejected")
        void rejectsLongBarcode() {
            String long41 = "x".repeat(41);
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", null, long41,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("null baseUom rejected")
        void rejectsNullBaseUom() {
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", null, null,
                    null, TrackingType.NONE, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("baseUom");
        }

        @Test
        @DisplayName("null trackingType rejected")
        void rejectsNullTrackingType() {
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("trackingType");
        }

        @Test
        @DisplayName("negative weightGrams rejected")
        void rejectsNegativeWeight() {
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, -1, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank actor rejected")
        void rejectsBlankActor() {
            assertThatThrownBy(() -> Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, " "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("actorId");
        }

        @Test
        @DisplayName("null optional fields are accepted")
        void nullOptionalFields() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThat(sku.getDescription()).isNull();
            assertThat(sku.getBarcode()).isNull();
            assertThat(sku.getWeightGrams()).isNull();
            assertThat(sku.getVolumeMl()).isNull();
            assertThat(sku.getHazardClass()).isNull();
            assertThat(sku.getShelfLifeDays()).isNull();
        }
    }

    @Nested
    @DisplayName("applyUpdate()")
    class Update {

        @Test
        @DisplayName("mutates only provided non-null fields")
        void updatePartial() throws InterruptedException {
            Sku sku = Sku.create("SKU-1", "Orig", "Orig desc", "111",
                    BaseUom.EA, TrackingType.NONE, 100, 50, null, null, ACTOR);
            Thread.sleep(1);

            sku.applyUpdate("Renamed", null, "222", null, null, "HAZ-1", 7, "actor-2");

            assertThat(sku.getName()).isEqualTo("Renamed");
            assertThat(sku.getDescription()).isEqualTo("Orig desc");
            assertThat(sku.getBarcode()).isEqualTo("222");
            assertThat(sku.getWeightGrams()).isEqualTo(100);
            assertThat(sku.getVolumeMl()).isEqualTo(50);
            assertThat(sku.getHazardClass()).isEqualTo("HAZ-1");
            assertThat(sku.getShelfLifeDays()).isEqualTo(7);
            assertThat(sku.getUpdatedBy()).isEqualTo("actor-2");
            assertThat(sku.getUpdatedAt()).isAfter(sku.getCreatedAt());
        }

        @Test
        @DisplayName("rejects invalid new name")
        void rejectsInvalidName() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> sku.applyUpdate("", null, null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange throws on skuCode change")
        void rejectImmutableSkuCode() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> sku.rejectImmutableChange("SKU-99", null, null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("skuCode");
        }

        @Test
        @DisplayName("rejectImmutableChange accepts a lowercase variant matching stored upper form")
        void rejectImmutableSkuCodeCaseInsensitive() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            // Storage is "SKU-1"; caller sent "sku-1" — same normalized value, no-op.
            sku.rejectImmutableChange("sku-1", null, null);
        }

        @Test
        @DisplayName("rejectImmutableChange throws on baseUom change")
        void rejectImmutableBaseUom() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> sku.rejectImmutableChange(null, BaseUom.KG, null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("baseUom");
        }

        @Test
        @DisplayName("rejectImmutableChange throws on trackingType change")
        void rejectImmutableTrackingType() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> sku.rejectImmutableChange(null, null, TrackingType.LOT))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("trackingType");
        }

        @Test
        @DisplayName("rejectImmutableChange tolerates matching values / nulls")
        void rejectImmutableNoChange() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            sku.rejectImmutableChange("SKU-1", BaseUom.EA, TrackingType.NONE);
            sku.rejectImmutableChange(null, null, null);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("deactivate: ACTIVE -> INACTIVE")
        void deactivateFromActive() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            sku.deactivate("actor-2");
            assertThat(sku.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(sku.isActive()).isFalse();
            assertThat(sku.getUpdatedBy()).isEqualTo("actor-2");
        }

        @Test
        @DisplayName("deactivate from INACTIVE throws")
        void deactivateFromInactive() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            sku.deactivate(ACTOR);
            assertThatThrownBy(() -> sku.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate: INACTIVE -> ACTIVE")
        void reactivateFromInactive() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            sku.deactivate(ACTOR);
            sku.reactivate("actor-3");
            assertThat(sku.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(sku.getUpdatedBy()).isEqualTo("actor-3");
        }

        @Test
        @DisplayName("reactivate from ACTIVE throws")
        void reactivateFromActive() {
            Sku sku = Sku.create("SKU-1", "N", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> sku.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("restores full state from persistence fields")
        void reconstituteFromPersistence() {
            java.util.UUID id = java.util.UUID.randomUUID();
            java.time.Instant t = java.time.Instant.parse("2026-04-01T00:00:00Z");

            Sku sku = Sku.reconstitute(
                    id, "SKU-1", "Name", "desc", "BC",
                    BaseUom.EA, TrackingType.LOT,
                    100, 50, "HAZ-1", 30,
                    WarehouseStatus.INACTIVE, 5L,
                    t, "creator", t, "updater");

            assertThat(sku.getId()).isEqualTo(id);
            assertThat(sku.getSkuCode()).isEqualTo("SKU-1");
            assertThat(sku.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(sku.getVersion()).isEqualTo(5L);
            assertThat(sku.getTrackingType()).isEqualTo(TrackingType.LOT);
            assertThat(sku.getShelfLifeDays()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Identity {

        @Test
        @DisplayName("equality is based on id only")
        void equalityByIdOnly() {
            java.util.UUID id = java.util.UUID.randomUUID();
            Sku a = Sku.reconstitute(id, "SKU-1", "A", null, null,
                    BaseUom.EA, TrackingType.NONE, null, null, null, null,
                    WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            Sku b = Sku.reconstitute(id, "SKU-99", "B", null, null,
                    BaseUom.KG, TrackingType.LOT, 1, 1, null, 5,
                    WarehouseStatus.INACTIVE, 5L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
