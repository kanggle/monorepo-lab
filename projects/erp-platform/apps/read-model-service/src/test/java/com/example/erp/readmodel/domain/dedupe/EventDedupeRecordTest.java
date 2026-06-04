package com.example.erp.readmodel.domain.dedupe;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventDedupeRecordTest {

    @Test
    void capturesProvenanceFields() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        EventDedupeRecord record = EventDedupeRecord.of(
                "evt-1", "erp.masterdata.employee.changed.v1", "emp-1", at);

        assertThat(record.eventId()).isEqualTo("evt-1");
        assertThat(record.topic()).isEqualTo("erp.masterdata.employee.changed.v1");
        assertThat(record.aggregateId()).isEqualTo("emp-1");
        assertThat(record.processedAt()).isEqualTo(at);
    }

    @Test
    void rejectsNullEventId() {
        assertThatThrownBy(() -> EventDedupeRecord.of(null, "t", "a", Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
