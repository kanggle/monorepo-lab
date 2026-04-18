package com.wms.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WarehouseTest {

    private static final String ACTOR = "actor-uuid";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid inputs produce an ACTIVE warehouse with version 0")
        void createValid() {
            Warehouse warehouse = Warehouse.create("WH01", "Seoul Main", "Seoul, Korea", "Asia/Seoul", ACTOR);

            assertThat(warehouse.getWarehouseCode()).isEqualTo("WH01");
            assertThat(warehouse.getName()).isEqualTo("Seoul Main");
            assertThat(warehouse.getAddress()).isEqualTo("Seoul, Korea");
            assertThat(warehouse.getTimezone()).isEqualTo("Asia/Seoul");
            assertThat(warehouse.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(warehouse.getVersion()).isZero();
            assertThat(warehouse.getId()).isNotNull();
            assertThat(warehouse.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(warehouse.getUpdatedBy()).isEqualTo(ACTOR);
            assertThat(warehouse.getCreatedAt()).isNotNull();
            assertThat(warehouse.getUpdatedAt()).isNotNull();
            assertThat(warehouse.isActive()).isTrue();
        }

        @Test
        @DisplayName("nullable address is permitted")
        void createWithoutAddress() {
            Warehouse warehouse = Warehouse.create("WH02", "Secondary", null, "UTC", ACTOR);
            assertThat(warehouse.getAddress()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "WH", "wh01", "WHA01", "WAREHOUSE1", "WH1", "WH1234"})
        @DisplayName("invalid warehouseCode rejected")
        void createRejectsInvalidCode(String badCode) {
            assertThatThrownBy(() -> Warehouse.create(badCode, "Name", null, "UTC", ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("warehouseCode");
        }

        @Test
        @DisplayName("null warehouseCode rejected")
        void createRejectsNullCode() {
            assertThatThrownBy(() -> Warehouse.create(null, "Name", null, "UTC", ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank name rejected")
        void createRejectsBlankName() {
            assertThatThrownBy(() -> Warehouse.create("WH01", "  ", null, "UTC", ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("name exceeding 100 chars rejected")
        void createRejectsLongName() {
            String longName = "x".repeat(101);
            assertThatThrownBy(() -> Warehouse.create("WH01", longName, null, "UTC", ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("address exceeding 200 chars rejected")
        void createRejectsLongAddress() {
            String longAddress = "x".repeat(201);
            assertThatThrownBy(() -> Warehouse.create("WH01", "N", longAddress, "UTC", ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("invalid timezone rejected")
        void createRejectsInvalidTimezone() {
            assertThatThrownBy(() -> Warehouse.create("WH01", "N", null, "Not/AZone", ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("timezone");
        }

        @Test
        @DisplayName("blank actorId rejected")
        void createRejectsBlankActor() {
            assertThatThrownBy(() -> Warehouse.create("WH01", "N", null, "UTC", " "))
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
            Warehouse w = Warehouse.create("WH01", "Orig Name", "Orig Addr", "UTC", ACTOR);
            Thread.sleep(1); // ensure timestamp difference

            w.applyUpdate("New Name", null, null, "actor-2");

            assertThat(w.getName()).isEqualTo("New Name");
            assertThat(w.getAddress()).isEqualTo("Orig Addr");
            assertThat(w.getTimezone()).isEqualTo("UTC");
            assertThat(w.getUpdatedBy()).isEqualTo("actor-2");
            assertThat(w.getUpdatedAt()).isAfter(w.getCreatedAt());
        }

        @Test
        @DisplayName("invalid name change rejected")
        void updateRejectsInvalidName() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            assertThatThrownBy(() -> w.applyUpdate("", null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("invalid timezone change rejected")
        void updateRejectsInvalidTimezone() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            assertThatThrownBy(() -> w.applyUpdate(null, null, "Bogus", ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange throws on warehouseCode mutation attempt")
        void rejectImmutableCodeChange() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            assertThatThrownBy(() -> w.rejectImmutableChange("WH99"))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("warehouseCode");
        }

        @Test
        @DisplayName("rejectImmutableChange tolerates matching warehouseCode")
        void rejectImmutableCodeMatch() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            w.rejectImmutableChange("WH01");
            w.rejectImmutableChange(null);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("deactivate: ACTIVE -> INACTIVE")
        void deactivateFromActive() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            w.deactivate("actor-2");
            assertThat(w.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(w.isActive()).isFalse();
            assertThat(w.getUpdatedBy()).isEqualTo("actor-2");
        }

        @Test
        @DisplayName("deactivate from INACTIVE throws")
        void deactivateFromInactive() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            w.deactivate(ACTOR);
            assertThatThrownBy(() -> w.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate: INACTIVE -> ACTIVE")
        void reactivateFromInactive() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            w.deactivate(ACTOR);
            w.reactivate("actor-3");
            assertThat(w.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(w.getUpdatedBy()).isEqualTo("actor-3");
        }

        @Test
        @DisplayName("reactivate from ACTIVE throws")
        void reactivateFromActive() {
            Warehouse w = Warehouse.create("WH01", "N", null, "UTC", ACTOR);
            assertThatThrownBy(() -> w.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("reconstitute()")
    class Reconstitute {

        @Test
        @DisplayName("restores state from persistence fields")
        void reconstituteFromPersistence() {
            java.util.UUID id = java.util.UUID.randomUUID();
            java.time.Instant t = java.time.Instant.parse("2026-04-01T00:00:00Z");

            Warehouse w = Warehouse.reconstitute(
                    id, "WH01", "Name", "Addr", "UTC",
                    WarehouseStatus.INACTIVE, 5L,
                    t, "creator", t, "updater");

            assertThat(w.getId()).isEqualTo(id);
            assertThat(w.getWarehouseCode()).isEqualTo("WH01");
            assertThat(w.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(w.getVersion()).isEqualTo(5L);
            assertThat(w.getCreatedAt()).isEqualTo(t);
            assertThat(w.getCreatedBy()).isEqualTo("creator");
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Identity {

        @Test
        @DisplayName("equality is based on id only")
        void equalityByIdOnly() {
            java.util.UUID id = java.util.UUID.randomUUID();
            Warehouse a = Warehouse.reconstitute(id, "WH01", "A", null, "UTC",
                    WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            Warehouse b = Warehouse.reconstitute(id, "WH02", "B", null, "UTC",
                    WarehouseStatus.INACTIVE, 5L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
