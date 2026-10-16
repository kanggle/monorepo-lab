package com.wms.inventory.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NativeRowValuesTest {

    private static final Instant T = Instant.parse("2026-10-14T03:04:05.123456Z");

    @Test
    @DisplayName("Instant passes through")
    void instant() {
        assertThat(NativeRowValues.instant(T, "c")).isEqualTo(T);
    }

    @Test
    @DisplayName("java.sql.Timestamp converts")
    void timestamp() {
        assertThat(NativeRowValues.instant(Timestamp.from(T), "c")).isEqualTo(T);
    }

    @Test
    @DisplayName("OffsetDateTime converts regardless of offset")
    void offsetDateTime() {
        assertThat(NativeRowValues.instant(T.atOffset(ZoneOffset.ofHours(9)), "c")).isEqualTo(T);
        assertThat(NativeRowValues.instant(OffsetDateTime.ofInstant(T, ZoneOffset.UTC), "c"))
                .isEqualTo(T);
    }

    @Test
    @DisplayName("LocalDateTime is read as UTC")
    void localDateTime() {
        assertThat(NativeRowValues.instant(LocalDateTime.ofInstant(T, ZoneOffset.UTC), "c"))
                .isEqualTo(T);
    }

    @Test
    @DisplayName("null stays null (nullable column)")
    void nullKept() {
        assertThat(NativeRowValues.instant(null, "last_movement_at")).isNull();
    }

    @Test
    @DisplayName("unknown type fails naming the column and the type")
    void unknownType() {
        assertThatThrownBy(() -> NativeRowValues.instant("2026-10-14", "occurred_at"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("occurred_at")
                .hasMessageContaining("java.lang.String");
    }
}
