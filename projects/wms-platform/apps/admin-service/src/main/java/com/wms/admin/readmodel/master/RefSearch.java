package com.wms.admin.readmodel.master;

import java.util.List;
import java.util.Locale;

/**
 * Parameter shaping for the master-ref {@code search} queries
 * ({@code admin-service-api.md § 1.7}, TASK-BE-630).
 *
 * <p>The LIKE pattern is built here, not in JPQL: {@code CONCAT('%', :q, '%')} with an
 * unfiltered (null) {@code q} hands PostgreSQL an untyped null inside an expression —
 * the same 42P18 class as TASK-BE-331. A pattern that is either a typed string or
 * {@code null} keeps every query's {@code :q IS NULL} guard plain.
 */
public final class RefSearch {

    /** The escape character every {@code search} query declares with {@code ESCAPE}. */
    public static final char ESCAPE = '\\';

    private RefSearch() {
    }

    /**
     * {@code null} for a blank/absent {@code q}; otherwise a lower-cased
     * {@code %…%} substring pattern with {@code % _ \} escaped so they match literally
     * (a user typing {@code WH_01} must not match {@code WHX01}).
     */
    public static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String trimmed = q.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(trimmed.length() + 2).append('%');
        for (char c : trimmed.toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    /** Blank status = no filter. */
    public static String status(String status) {
        return status == null || status.isBlank() ? null : status.trim();
    }

    /**
     * An {@code IN} list is never bound empty (Hibernate renders {@code IN ()}, which
     * PostgreSQL rejects); an absent filter is carried by the {@code anyType} flag and
     * the list gets a placeholder that the flag short-circuits past.
     */
    public static List<String> types(List<String> types) {
        if (types == null) {
            return List.of("");
        }
        List<String> cleaned = types.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .toList();
        return cleaned.isEmpty() ? List.of("") : cleaned;
    }

    public static boolean anyType(List<String> types) {
        return types == null || types.stream().allMatch(t -> t == null || t.isBlank());
    }
}
