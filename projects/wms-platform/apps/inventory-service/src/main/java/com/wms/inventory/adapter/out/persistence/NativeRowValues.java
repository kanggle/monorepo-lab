package com.wms.inventory.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Reads column values out of native-query result rows ({@code Object[]}).
 *
 * <p>The Java type a driver/Hibernate hands back for a {@code TIMESTAMPTZ} column is not
 * something the caller controls: PostgreSQL + Hibernate 6 returns {@link Instant}, older stacks
 * returned {@link Timestamp}. TASK-BE-637: casting to one of them 500'd every list endpoint.
 * Read the value through here and never assume its class.
 */
public final class NativeRowValues {

    private NativeRowValues() {
    }

    /**
     * Converts a timestamp column value to an {@link Instant}.
     *
     * @param value  the raw column value; {@code null} is kept as {@code null}
     * @param column column name, used only in the failure message
     * @throws IllegalStateException when the value is none of {@link Instant}, {@link Timestamp},
     *                               {@link OffsetDateTime}, {@link LocalDateTime}
     */
    public static Instant instant(Object value, String column) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof OffsetDateTime o) {
            return o.toInstant();
        }
        if (value instanceof LocalDateTime l) {
            // A zone-less value carries no offset; the service stores and exchanges UTC.
            return l.toInstant(ZoneOffset.UTC);
        }
        throw new IllegalStateException("Column '" + column
                + "' returned unsupported timestamp type " + value.getClass().getName());
    }
}
