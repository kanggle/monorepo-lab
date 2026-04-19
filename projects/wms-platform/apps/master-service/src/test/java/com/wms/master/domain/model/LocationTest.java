package com.wms.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocationTest {

    private static final String ACTOR = "actor-uuid";
    private static final String WAREHOUSE_CODE = "WH01";
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();
    private static final UUID ZONE_ID = UUID.randomUUID();
    private static final String VALID_CODE = "WH01-A-01-02-03";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid inputs produce an ACTIVE location with version 0")
        void createValid() {
            Location loc = Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, "01", "02", "03", null,
                    LocationType.STORAGE, 500, ACTOR);

            assertThat(loc.getId()).isNotNull();
            assertThat(loc.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(loc.getZoneId()).isEqualTo(ZONE_ID);
            assertThat(loc.getLocationCode()).isEqualTo(VALID_CODE);
            assertThat(loc.getAisle()).isEqualTo("01");
            assertThat(loc.getRack()).isEqualTo("02");
            assertThat(loc.getLevel()).isEqualTo("03");
            assertThat(loc.getBin()).isNull();
            assertThat(loc.getLocationType()).isEqualTo(LocationType.STORAGE);
            assertThat(loc.getCapacityUnits()).isEqualTo(500);
            assertThat(loc.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(loc.getVersion()).isZero();
            assertThat(loc.isActive()).isTrue();
            assertThat(loc.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(loc.getUpdatedBy()).isEqualTo(ACTOR);
        }

        @Test
        @DisplayName("optional capacityUnits null is allowed")
        void createNullCapacityUnits() {
            Location loc = Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR);
            assertThat(loc.getCapacityUnits()).isNull();
        }

        @Test
        @DisplayName("null warehouseCode rejected")
        void nullWarehouseCode() {
            assertThatThrownBy(() -> Location.create(
                    null, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("warehouseCode");
        }

        @Test
        @DisplayName("null warehouseId rejected")
        void nullWarehouseId() {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, null, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("warehouseId");
        }

        @Test
        @DisplayName("null zoneId rejected")
        void nullZoneId() {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, null,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("zoneId");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "", " ", "wh01-a-01-02-03",         // lowercase
                "WH01-", "WH01",                     // too few segments
                "WH-A-01",                           // wrong warehouse prefix shape
                "WH01-A",                            // only 2 segments — pattern requires 3+
                "WH01-A!-01-02-03",                  // bang char
                "WH01_A_01_02_03",                   // underscore
                "WH01-A-01-02-03-04-05-06-07"        // too many segments (>6 total)
        })
        @DisplayName("invalid locationCode pattern rejected")
        void invalidPattern(String badCode) {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    badCode, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("locationCode");
        }

        @Test
        @DisplayName("locationCode not starting with warehouseCode prefix rejected")
        void prefixMismatch() {
            // Pattern is valid but prefix doesn't match parent WH02 warehouse
            assertThatThrownBy(() -> Location.create(
                    "WH02", WAREHOUSE_ID, ZONE_ID,
                    "WH01-A-01-01-01", null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("WH02-");
        }

        @Test
        @DisplayName("locationCode longer than 40 characters rejected")
        void codeTooLong() {
            // 41 chars total
            String longCode = "WH01-AAAAAAAAAA-BBBBBBBBB-CCCCCC-DDDD-EE";
            assertThat(longCode.length()).isEqualTo(40);
            String tooLong = longCode + "X";
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    tooLong, null, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("null locationType rejected")
        void nullLocationType() {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("locationType");
        }

        @Test
        @DisplayName("capacityUnits < 1 rejected")
        void capacityTooSmall() {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, 0, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("capacityUnits");

            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, -5, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"aisle-lower", " ", "AB!", "AAAAAAAAAAA"}) // 11 chars last one
        @DisplayName("invalid hierarchy field rejected")
        void invalidHierarchyField(String bad) {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, bad, null, null, null,
                    LocationType.STORAGE, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank actorId rejected")
        void blankActor() {
            assertThatThrownBy(() -> Location.create(
                    WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, " "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("actorId");
        }
    }

    @Nested
    @DisplayName("applyUpdate()")
    class Update {

        @Test
        @DisplayName("updates only provided non-null fields")
        void partialUpdate() throws InterruptedException {
            Location loc = newLocation();
            Thread.sleep(1);

            loc.applyUpdate(LocationType.DAMAGED, 200, null, null, null, null, "actor-2");

            assertThat(loc.getLocationType()).isEqualTo(LocationType.DAMAGED);
            assertThat(loc.getCapacityUnits()).isEqualTo(200);
            assertThat(loc.getAisle()).isEqualTo("01");      // unchanged
            assertThat(loc.getRack()).isEqualTo("02");       // unchanged
            assertThat(loc.getUpdatedBy()).isEqualTo("actor-2");
            assertThat(loc.getUpdatedAt()).isAfter(loc.getCreatedAt());
        }

        @Test
        @DisplayName("hierarchy fields update")
        void hierarchyUpdate() {
            Location loc = newLocation();
            loc.applyUpdate(null, null, "05", "06", "07", "08", ACTOR);
            assertThat(loc.getAisle()).isEqualTo("05");
            assertThat(loc.getRack()).isEqualTo("06");
            assertThat(loc.getLevel()).isEqualTo("07");
            assertThat(loc.getBin()).isEqualTo("08");
        }

        @Test
        @DisplayName("capacityUnits < 1 on update rejected")
        void updateCapacityInvalid() {
            Location loc = newLocation();
            assertThatThrownBy(() -> loc.applyUpdate(null, 0, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange on locationCode attempt")
        void rejectImmutableLocationCode() {
            Location loc = newLocation();
            assertThatThrownBy(() -> loc.rejectImmutableChange("WH01-B-09-09-09", null, null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("locationCode");
        }

        @Test
        @DisplayName("rejectImmutableChange on warehouseId attempt")
        void rejectImmutableWarehouseId() {
            Location loc = newLocation();
            assertThatThrownBy(() -> loc.rejectImmutableChange(null, UUID.randomUUID(), null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("warehouseId");
        }

        @Test
        @DisplayName("rejectImmutableChange on zoneId attempt")
        void rejectImmutableZoneId() {
            Location loc = newLocation();
            assertThatThrownBy(() -> loc.rejectImmutableChange(null, null, UUID.randomUUID()))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("zoneId");
        }

        @Test
        @DisplayName("rejectImmutableChange tolerates matching or null values")
        void rejectImmutableChangeTolerant() {
            Location loc = newLocation();
            loc.rejectImmutableChange(null, null, null);
            loc.rejectImmutableChange(VALID_CODE, WAREHOUSE_ID, ZONE_ID);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("deactivate ACTIVE -> INACTIVE")
        void deactivateActive() {
            Location loc = newLocation();
            loc.deactivate("actor-2");
            assertThat(loc.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(loc.isActive()).isFalse();
            assertThat(loc.getUpdatedBy()).isEqualTo("actor-2");
        }

        @Test
        @DisplayName("deactivate from INACTIVE throws")
        void doubleDeactivate() {
            Location loc = newLocation();
            loc.deactivate(ACTOR);
            assertThatThrownBy(() -> loc.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate INACTIVE -> ACTIVE")
        void reactivateInactive() {
            Location loc = newLocation();
            loc.deactivate(ACTOR);
            loc.reactivate("actor-3");
            assertThat(loc.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(loc.getUpdatedBy()).isEqualTo("actor-3");
        }

        @Test
        @DisplayName("reactivate from ACTIVE throws")
        void reactivateFromActive() {
            Location loc = newLocation();
            assertThatThrownBy(() -> loc.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("restores state from persistence fields")
        void reconstituteFromPersistence() {
            UUID id = UUID.randomUUID();
            java.time.Instant t = java.time.Instant.parse("2026-04-01T00:00:00Z");

            Location loc = Location.reconstitute(
                    id, WAREHOUSE_ID, ZONE_ID,
                    VALID_CODE, "01", "02", "03", null,
                    LocationType.QUARANTINE, 42,
                    WarehouseStatus.INACTIVE, 7L,
                    t, "creator", t, "updater");

            assertThat(loc.getId()).isEqualTo(id);
            assertThat(loc.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(loc.getVersion()).isEqualTo(7L);
            assertThat(loc.getCapacityUnits()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Identity {

        @Test
        @DisplayName("equality is based on id only")
        void idEquality() {
            UUID id = UUID.randomUUID();
            Location a = Location.reconstitute(
                    id, WAREHOUSE_ID, ZONE_ID, VALID_CODE, null, null, null, null,
                    LocationType.STORAGE, null, WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            Location b = Location.reconstitute(
                    id, UUID.randomUUID(), UUID.randomUUID(), "WH01-B-01-01-01",
                    null, null, null, null, LocationType.DAMAGED, 5,
                    WarehouseStatus.INACTIVE, 9L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }

    private static Location newLocation() {
        return Location.create(
                WAREHOUSE_CODE, WAREHOUSE_ID, ZONE_ID,
                VALID_CODE, "01", "02", "03", null,
                LocationType.STORAGE, 500, ACTOR);
    }
}
