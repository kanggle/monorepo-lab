package com.wms.master.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.master.domain.exception.ImmutableFieldException;
import com.wms.master.domain.exception.InvalidStateTransitionException;
import com.wms.master.domain.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PartnerTest {

    private static final String ACTOR = "actor-uuid";

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid inputs produce an ACTIVE Partner at version 0")
        void createValid() {
            Partner partner = Partner.create(
                    "SUP-001", "ACME Supplier", PartnerType.SUPPLIER,
                    "123-45-67890", "Jane Kim",
                    "jane@acme.example.com", "+82-2-1234-5678",
                    "Seoul, Korea", ACTOR);

            assertThat(partner.getPartnerCode()).isEqualTo("SUP-001");
            assertThat(partner.getName()).isEqualTo("ACME Supplier");
            assertThat(partner.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
            assertThat(partner.getBusinessNumber()).isEqualTo("123-45-67890");
            assertThat(partner.getContactName()).isEqualTo("Jane Kim");
            assertThat(partner.getContactEmail()).isEqualTo("jane@acme.example.com");
            assertThat(partner.getContactPhone()).isEqualTo("+82-2-1234-5678");
            assertThat(partner.getAddress()).isEqualTo("Seoul, Korea");
            assertThat(partner.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(partner.getVersion()).isZero();
            assertThat(partner.getId()).isNotNull();
            assertThat(partner.getCreatedBy()).isEqualTo(ACTOR);
            assertThat(partner.getUpdatedBy()).isEqualTo(ACTOR);
            assertThat(partner.isActive()).isTrue();
        }

        @Test
        @DisplayName("partnerCode is trimmed but case is preserved")
        void preservesPartnerCodeCase() {
            // Distinct from SKU — partnerCode is stored as-supplied, no uppercase fold.
            Partner partner = Partner.create(
                    "  SUP-001  ", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            assertThat(partner.getPartnerCode()).isEqualTo("SUP-001");
        }

        @Test
        @DisplayName("null or blank partnerCode rejected")
        void rejectsBlankCode() {
            assertThatThrownBy(() -> Partner.create(null, "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("partnerCode");
            assertThatThrownBy(() -> Partner.create("  ", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("partnerCode over 20 chars rejected")
        void rejectsLongPartnerCode() {
            String long21 = "x".repeat(21);
            assertThatThrownBy(() -> Partner.create(long21, "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank name rejected")
        void rejectsBlankName() {
            assertThatThrownBy(() -> Partner.create("SUP-1", "", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("name over 200 chars rejected")
        void rejectsLongName() {
            String long201 = "x".repeat(201);
            assertThatThrownBy(() -> Partner.create("SUP-1", long201, PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("null partnerType rejected")
        void rejectsNullPartnerType() {
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", null,
                    null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("partnerType");
        }

        @Test
        @DisplayName("businessNumber over 20 chars rejected")
        void rejectsLongBusinessNumber() {
            String long21 = "x".repeat(21);
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    long21, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("contactName over 100 chars rejected")
        void rejectsLongContactName() {
            String long101 = "x".repeat(101);
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, long101, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("contactEmail over 200 chars rejected")
        void rejectsLongContactEmail() {
            String long201 = "x".repeat(180) + "@example.com";
            // 192 chars total — within
            Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, long201, null, null, ACTOR);
            String over = "x".repeat(200) + "@example.com";
            assertThatThrownBy(() -> Partner.create("SUP-2", "N", PartnerType.SUPPLIER,
                    null, null, over, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("contactEmail without '@' rejected")
        void rejectsMalformedEmail() {
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, "notanemail", null, null, ACTOR))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("contactEmail");
        }

        @Test
        @DisplayName("contactPhone over 30 chars rejected")
        void rejectsLongContactPhone() {
            String long31 = "+".repeat(31);
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, long31, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("address over 300 chars rejected")
        void rejectsLongAddress() {
            String long301 = "x".repeat(301);
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, long301, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank actor rejected")
        void rejectsBlankActor() {
            assertThatThrownBy(() -> Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, " "))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("actorId");
        }

        @Test
        @DisplayName("all optional fields may be null")
        void nullOptionalFields() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.CUSTOMER,
                    null, null, null, null, null, ACTOR);
            assertThat(partner.getBusinessNumber()).isNull();
            assertThat(partner.getContactName()).isNull();
            assertThat(partner.getContactEmail()).isNull();
            assertThat(partner.getContactPhone()).isNull();
            assertThat(partner.getAddress()).isNull();
        }

        @Test
        @DisplayName("partnerType BOTH is accepted")
        void acceptsBothPartnerType() {
            Partner partner = Partner.create("OMNI-1", "N", PartnerType.BOTH,
                    null, null, null, null, null, ACTOR);
            assertThat(partner.getPartnerType()).isEqualTo(PartnerType.BOTH);
        }
    }

    @Nested
    @DisplayName("applyUpdate()")
    class Update {

        @Test
        @DisplayName("mutates only provided non-null fields")
        void updatePartial() throws InterruptedException {
            Partner partner = Partner.create("SUP-1", "Orig", PartnerType.SUPPLIER,
                    "B-1", "C-Name", "c@example.com", "+82-1234", "Seoul", ACTOR);
            Thread.sleep(1);

            partner.applyUpdate("Renamed", null, "B-2", null, "new@example.com",
                    null, "Busan", "actor-2");

            assertThat(partner.getName()).isEqualTo("Renamed");
            assertThat(partner.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
            assertThat(partner.getBusinessNumber()).isEqualTo("B-2");
            assertThat(partner.getContactName()).isEqualTo("C-Name");
            assertThat(partner.getContactEmail()).isEqualTo("new@example.com");
            assertThat(partner.getContactPhone()).isEqualTo("+82-1234");
            assertThat(partner.getAddress()).isEqualTo("Busan");
            assertThat(partner.getUpdatedBy()).isEqualTo("actor-2");
            assertThat(partner.getUpdatedAt()).isAfter(partner.getCreatedAt());
        }

        @Test
        @DisplayName("partnerType can be changed (SUPPLIER → BOTH)")
        void mutablePartnerType() {
            Partner partner = Partner.create("P-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.applyUpdate(null, PartnerType.BOTH, null, null, null, null, null, ACTOR);
            assertThat(partner.getPartnerType()).isEqualTo(PartnerType.BOTH);
        }

        @Test
        @DisplayName("rejects invalid new name")
        void rejectsInvalidName() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> partner.applyUpdate("", null, null, null, null, null, null, ACTOR))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("rejectImmutableChange throws on partnerCode change")
        void rejectImmutablePartnerCode() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> partner.rejectImmutableChange("SUP-99"))
                    .isInstanceOf(ImmutableFieldException.class)
                    .hasMessageContaining("partnerCode");
        }

        @Test
        @DisplayName("rejectImmutableChange tolerates matching values / null")
        void rejectImmutableNoChange() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.rejectImmutableChange("SUP-1");
            partner.rejectImmutableChange(null);
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        @DisplayName("deactivate: ACTIVE -> INACTIVE")
        void deactivateFromActive() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.deactivate("actor-2");
            assertThat(partner.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(partner.isActive()).isFalse();
            assertThat(partner.getUpdatedBy()).isEqualTo("actor-2");
        }

        @Test
        @DisplayName("deactivate from INACTIVE throws")
        void deactivateFromInactive() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.deactivate(ACTOR);
            assertThatThrownBy(() -> partner.deactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("reactivate: INACTIVE -> ACTIVE")
        void reactivateFromInactive() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.deactivate(ACTOR);
            partner.reactivate("actor-3");
            assertThat(partner.getStatus()).isEqualTo(WarehouseStatus.ACTIVE);
            assertThat(partner.getUpdatedBy()).isEqualTo("actor-3");
        }

        @Test
        @DisplayName("reactivate from ACTIVE throws")
        void reactivateFromActive() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> partner.reactivate(ACTOR))
                    .isInstanceOf(InvalidStateTransitionException.class);
        }

        @Test
        @DisplayName("blank actor on deactivate rejected")
        void blankActorOnDeactivate() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            assertThatThrownBy(() -> partner.deactivate(""))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("blank actor on reactivate rejected")
        void blankActorOnReactivate() {
            Partner partner = Partner.create("SUP-1", "N", PartnerType.SUPPLIER,
                    null, null, null, null, null, ACTOR);
            partner.deactivate(ACTOR);
            assertThatThrownBy(() -> partner.reactivate(""))
                    .isInstanceOf(ValidationException.class);
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

            Partner partner = Partner.reconstitute(
                    id, "SUP-1", "Name", PartnerType.BOTH,
                    "B-1", "C-Name", "c@example.com", "+82-1", "Seoul",
                    WarehouseStatus.INACTIVE, 5L,
                    t, "creator", t, "updater");

            assertThat(partner.getId()).isEqualTo(id);
            assertThat(partner.getPartnerCode()).isEqualTo("SUP-1");
            assertThat(partner.getPartnerType()).isEqualTo(PartnerType.BOTH);
            assertThat(partner.getStatus()).isEqualTo(WarehouseStatus.INACTIVE);
            assertThat(partner.getVersion()).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class Identity {

        @Test
        @DisplayName("equality is based on id only")
        void equalityByIdOnly() {
            java.util.UUID id = java.util.UUID.randomUUID();
            Partner a = Partner.reconstitute(id, "SUP-1", "A", PartnerType.SUPPLIER,
                    null, null, null, null, null,
                    WarehouseStatus.ACTIVE, 0L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            Partner b = Partner.reconstitute(id, "CUST-99", "B", PartnerType.CUSTOMER,
                    "X", "Y", "z@example.com", "+1", "Tokyo",
                    WarehouseStatus.INACTIVE, 5L,
                    java.time.Instant.now(), ACTOR, java.time.Instant.now(), ACTOR);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
