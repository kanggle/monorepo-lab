package com.wms.inbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionTest {

    private static final Instant NOW = Instant.parse("2026-04-29T10:00:00Z");
    private static final UUID INSPECTION_ID = UUID.randomUUID();

    // -------------------------------------------------------------------------
    // allDiscrepanciesAcknowledged
    // -------------------------------------------------------------------------

    @Test
    void allDiscrepanciesAcknowledged_whenNoDiscrepancies_returnsTrue() {
        Inspection inspection = inspection(List.of());
        assertThat(inspection.allDiscrepanciesAcknowledged()).isTrue();
    }

    @Test
    void allDiscrepanciesAcknowledged_whenAllAcknowledged_returnsTrue() {
        InspectionDiscrepancy disc = discrepancy(true);
        assertThat(inspection(List.of(disc)).allDiscrepanciesAcknowledged()).isTrue();
    }

    @Test
    void allDiscrepanciesAcknowledged_whenOneUnacknowledged_returnsFalse() {
        InspectionDiscrepancy disc = discrepancy(false);
        assertThat(inspection(List.of(disc)).allDiscrepanciesAcknowledged()).isFalse();
    }

    @Test
    void allDiscrepanciesAcknowledged_mixedAck_returnsFalse() {
        assertThat(inspection(List.of(discrepancy(true), discrepancy(false)))
                .allDiscrepanciesAcknowledged()).isFalse();
    }

    // -------------------------------------------------------------------------
    // countUnacknowledgedDiscrepancies
    // -------------------------------------------------------------------------

    @Test
    void countUnacknowledgedDiscrepancies_returnsOnlyUnackedCount() {
        Inspection inspection = inspection(List.of(
                discrepancy(true), discrepancy(false), discrepancy(false)));
        assertThat(inspection.countUnacknowledgedDiscrepancies()).isEqualTo(2);
    }

    @Test
    void countUnacknowledgedDiscrepancies_whenNone_returnsZero() {
        assertThat(inspection(List.of()).countUnacknowledgedDiscrepancies()).isZero();
    }

    // -------------------------------------------------------------------------
    // InspectionDiscrepancy.acknowledge
    // -------------------------------------------------------------------------

    @Test
    void acknowledge_setsAcknowledgedFields() {
        InspectionDiscrepancy disc = discrepancy(false);
        disc.acknowledge("admin", NOW);
        assertThat(disc.isAcknowledged()).isTrue();
        assertThat(disc.getAcknowledgedBy()).isEqualTo("admin");
        assertThat(disc.getAcknowledgedAt()).isEqualTo(NOW);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Inspection inspection(List<InspectionDiscrepancy> discrepancies) {
        return new Inspection(
                INSPECTION_ID, UUID.randomUUID(), "inspector-1",
                null, "notes", 0L,
                NOW, "actor", NOW, "actor",
                List.of(), discrepancies);
    }

    private static InspectionDiscrepancy discrepancy(boolean acknowledged) {
        InspectionDiscrepancy d = InspectionDiscrepancy.createNew(
                UUID.randomUUID(), INSPECTION_ID, UUID.randomUUID(),
                DiscrepancyType.QUANTITY_MISMATCH, 100, 80);
        if (acknowledged) {
            d.acknowledge("admin", NOW);
        }
        return d;
    }
}
