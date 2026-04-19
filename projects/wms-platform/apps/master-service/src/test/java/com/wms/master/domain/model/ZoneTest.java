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

class ZoneTest {

    private static final String ACTOR = "actor-uuid";
    private static final UUID WAREHOUSE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid inputs produce an ACTIVE zone with version 0")
        void createValid() {
            Zone zone = Zone.create(WAREHOUSE_ID, "Z-A", "Ambient A", ZoneType.AMBIENT, ACTOR);

            assertThat(zone.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(zone.getZoneCode()).isEqualTo("Z-A");
            assertThat(zone.getName()).isEqualTo("Ambient A");
            assertThat(zone.getZoneType()).isEqualTo(ZoneType.AMBIENT);
            assertThat(zone.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(zone.getVersion()).isZero();
            assertThat(zone.getId()).isNotNull();
            assertThat(zone.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(zone.getUpdatedBy()).isEqualTo(ACTOR);
            assertThat(zone.getCreatedAt()).isNotNull();
            assertThat(zone.getUpdatedAt()).isNotNull();
            assertThat(zone.isActive()).isTrue();
        }

        @Test
        @DisplayName("null warehouseId rejected")
        void createRejectsNullWarehouseId() {
            assertThatThrownBy(() ->
                    Zone.create(null, "Z-A", "Ambient", ZoneType.AMBIENT, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("warehouseId");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "z-a", "Z-", "Z_A", "Z-a", "ZONE-A", "Z-AB!", "Z A"})
        @DisplayName("invalid zoneCode rejected")
        void createRejectsInvalidCode(String badCode) {
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, badCode, "Name", ZoneType.AMBIENT, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("zoneCode");
        }

        @Test
        @DisplayName("null zoneCode rejected")
        void createRejectsNullCode() {
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, null, "Name", ZoneType.AMBIENT, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank name rejected")
        void createRejectsBlankName() {
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, "Z-A", " ", ZoneType.AMBIENT, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("name exceeding 100 chars rejected")
        void createRejectsLongName() {
            String longName = "x".repeat(101);
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, "Z-A", longName, ZoneType.AMBIENT, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("null zoneType rejected")
        void createRejectsNullZoneType() {
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, "Z-A", "Name", null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("zoneType");
        }

        @Test
        @DisplayName("blank actorId rejected")
        void createRejectsBlankActor() {
            assertThatThrownBy(() ->
                    Zone.create(WAREHOUSE_ID, "Z-A", "Name", ZoneType.AMBIENT, " "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("actorId");
        }
    }

    @Nested
    @DisplayName("applyUpdate()")
    class Update {

        @Test
        @DisplayName("updates only provided non-null fields")
        void updatePartial() throws InterruptedException {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "Orig", ZoneType.AMBIENT, ACTOR);
            Thread.sleep(1);

            z.applyUpdate("New Name", null, "actor-2");

            assertThat(z.getName()).isEqualTo("New Name");
            assertThat(z.getZoneType()).isEqualTo(ZoneType.AMBIENT);
            assertThat(z.getUpdatedBy()).isEqualTo("actor-2");
            assertThat(z.getUpdatedAt()).isAfter(z.getCreatedAt());
        }

        @Test
        @DisplayName("zoneType change takes effect")
        void updateZoneType() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "Name", ZoneType.AMBIENT, ACTOR);
            z.applyUpdate(null, ZoneType.CHILLED, ACTOR);
            assertThat(z.getZoneType()).isEqualTo(ZoneType.CHILLED);
        }

        @Test
        @DisplayName("invalid name change rejected")
        void updateRejectsInvalidName() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            assertThatThrownBy(() -> z.applyUpdate("", null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange throws on zoneCode mutation attempt")
        void rejectImmutableCodeChange() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            assertThatThrownBy(() -> z.rejectImmutableChange("Z-B", null))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("zoneCode");
        }

        @Test
        @DisplayName("rejectImmutableChange throws on warehouseId mutation attempt")
        void rejectImmutableWarehouseIdChange() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            assertThatThrownBy(() ->
                    z.rejectImmutableChange(null, UUID.randomUUID()))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("warehouseId");
        }

        @Test
        @DisplayName("rejectImmutableChange tolerates matching or null values")
        void rejectImmutableChangeTolerant() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            z.rejectImmutableChange(null, null);
            z.rejectImmutableChange("Z-A", WAREHOUSE_ID);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("deactivate: ACTIVE -> INACTIVE")
        void deactivateFromActive() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            z.deactivate("actor-2");
            assertThat(z.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(z.isActive()).isFalse();
            assertThat(z.getUpdatedBy()).isEqualTo("actor-2");
        }

        @Test
        @DisplayName("deactivate from INACTIVE throws")
        void deactivateFromInactive() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            z.deactivate(ACTOR);
            assertThatThrownBy(() -> z.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate: INACTIVE -> ACTIVE")
        void reactivateFromInactive() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            z.deactivate(ACTOR);
            z.reactivate("actor-3");
            assertThat(z.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(z.getUpdatedBy()).isEqualTo("actor-3");
        }

        @Test
        @DisplayName("reactivate from ACTIVE throws")
        void reactivateFromActive() {
            Zone z = Zone.create(WAREHOUSE_ID, "Z-A", "N", ZoneType.AMBIENT, ACTOR);
            assertThatThrownBy(() -> z.reactivate(ACTOR))
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

            Zone z = Zone.reconstitute(
                    id, WAREHOUSE_ID, "Z-A", "Name", ZoneType.FROZEN,
                    WarehouseStatus.INACTIVE, 5L,
                    t, "creator", t, "updater");

            assertThat(z.getId()).isEqualTo(id);
            assertThat(z.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(z.getZoneCode()).isEqualTo("Z-A");
            assertThat(z.getZoneType()).isEqualTo(ZoneType.FROZEN);
            assertThat(z.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(z.getVersion()).isEqualTo(5L);
            assertThat(z.getCreatedAt()).isEqualTo(t);
            assertThat(z.getCreatedBy()).isEqualTo("creator");
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Identity {

        @Test
        @DisplayName("equality is based on id only")
        void equalityByIdOnly() {
            UUID id = UUID.randomUUID();
            Zone a = Zone.reconstitute(id, WAREHOUSE_ID, "Z-A", "A", ZoneType.AMBIENT,
                    WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            Zone b = Zone.reconstitute(id, UUID.randomUUID(), "Z-B", "B", ZoneType.CHILLED,
                    WarehouseStatus.INACTIVE, 5L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
