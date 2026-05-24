package com.wms.master.adapter.in.web.controller.support;

import com.wms.master.domain.exception.ValidationException;

/**
 * Shared static utility helpers used by all master-service REST controllers.
 *
 * <p>Extracted from the 7 controller classes to eliminate duplicate
 * {@code etag} / {@code sortField} / {@code sortDirection} / {@code parseEnum}
 * implementations (TASK-BE-294 L6 deduplication).
 *
 * <p>This class has no state and no Spring dependency — it is used via static
 * import only.
 */
public final class ControllerSupport {

    private ControllerSupport() {
        // utility class — no instantiation
    }

    /**
     * Formats an ETag header value from an aggregate version number.
     *
     * @param version the aggregate version
     * @return the ETag string, e.g. {@code "v3"}
     */
    public static String etag(long version) {
        return "\"v" + version + "\"";
    }

    /**
     * Extracts the sort field from a {@code field,direction} sort string.
     *
     * <p>If no comma is present the entire string is treated as the field name.
     *
     * @param sort the raw sort parameter, e.g. {@code "updatedAt,desc"}
     * @return the field segment, e.g. {@code "updatedAt"}
     */
    public static String sortField(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? sort : sort.substring(0, comma);
    }

    /**
     * Extracts the sort direction from a {@code field,direction} sort string.
     *
     * <p>If no comma is present, defaults to {@code "asc"}.
     *
     * @param sort the raw sort parameter, e.g. {@code "updatedAt,desc"}
     * @return the direction segment, e.g. {@code "desc"}
     */
    public static String sortDirection(String sort) {
        int comma = sort.indexOf(',');
        return comma < 0 ? "asc" : sort.substring(comma + 1);
    }

    /**
     * Parses a nullable / blank raw string into an enum constant of the given
     * type.  Returns {@code null} if {@code raw} is null or blank.  Throws a
     * {@link ValidationException} with the supplied {@code errorMessage} if the
     * value is present but does not match any constant (case-insensitive).
     *
     * @param raw          the raw query-parameter value
     * @param type         the enum class to parse into
     * @param errorMessage the human-readable validation error shown to callers
     * @param <T>          the enum type
     * @return the matching enum constant, or {@code null}
     */
    public static <T extends Enum<T>> T parseEnum(String raw, Class<T> type, String errorMessage) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(errorMessage);
        }
    }
}
