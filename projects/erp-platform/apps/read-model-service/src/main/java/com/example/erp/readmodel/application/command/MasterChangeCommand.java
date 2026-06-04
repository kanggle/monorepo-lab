package com.example.erp.readmodel.application.command;

import com.example.erp.readmodel.domain.common.ChangeKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Parsed master-change command handed to {@link
 * com.example.erp.readmodel.application.ApplyMasterChangeUseCase} by a Kafka
 * consumer. Carries the envelope metadata needed for dedupe / provenance plus
 * the projection {@code after} field map (already extracted by the consumer's
 * envelope parser — the application layer touches no Kafka type).
 *
 * <p>{@code after} is {@code null} for a {@code RETIRED} change. {@code afterField}
 * helpers tolerate a {@code null} map (RETIRED) and missing keys.
 */
public record MasterChangeCommand(
        String eventId,
        String topic,
        String aggregateId,
        ChangeKind changeKind,
        Instant occurredAt,
        Map<String, Object> after
) {

    public String afterString(String key) {
        if (after == null) {
            return null;
        }
        Object v = after.get(key);
        return v == null ? null : v.toString();
    }

    public int afterInt(String key, int defaultValue) {
        if (after == null) {
            return defaultValue;
        }
        Object v = after.get(key);
        if (v == null) {
            return defaultValue;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Reads {@code after.effectivePeriod.effectiveFrom|effectiveTo} as an
     * ISO-8601 date, tolerating absence / a flat key / a nested map. Returns
     * {@code null} when not present or unparseable (the projection keeps null —
     * effective-dating is best-effort retained for {@code ?asOf} parity, E2).
     */
    public LocalDate effectiveDate(String which) {
        if (after == null) {
            return null;
        }
        Object period = after.get("effectivePeriod");
        Object raw = null;
        if (period instanceof Map<?, ?> m) {
            raw = m.get(which);
        }
        if (raw == null) {
            raw = after.get(which);
        }
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw.toString());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
