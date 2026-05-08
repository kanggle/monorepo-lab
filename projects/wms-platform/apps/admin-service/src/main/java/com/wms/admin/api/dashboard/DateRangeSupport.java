package com.wms.admin.api.dashboard;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Shared validator for optional dashboard date-range filters
 * ({@code requiredShipDateFrom/To}, {@code shippedAtFrom/To}, etc.).
 * Throws {@link IllegalArgumentException} (mapped to 400 {@code VALIDATION_ERROR}
 * by {@code GlobalExceptionHandler}) when {@code from > to}.
 */
final class DateRangeSupport {

    private DateRangeSupport() {}

    static void validate(String paramName, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(paramName + "From must not be after " + paramName + "To");
        }
    }

    static void validate(String paramName, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(paramName + "From must not be after " + paramName + "To");
        }
    }
}
