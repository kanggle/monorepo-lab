package com.wms.admin.infra.persistence;

import java.util.Locale;

/**
 * LIKE-pattern shaping for the {@code search} queries (TASK-BE-652).
 *
 * <p>The pattern is built here, not in JPQL: {@code CONCAT(:p, '%')} with an absent
 * (null) filter hands PostgreSQL a null bound as {@code bytea} inside the expression
 * ("operator does not exist: character varying ~~ bytea" — an HTTP 500 on every
 * unfiltered call). A pattern that is either a typed string or {@code null} keeps the
 * query's {@code :p IS NULL} guard plain — same fix as the master-ref read model's
 * {@code RefSearch.likePattern} (TASK-BE-630 / TASK-BE-331).
 *
 * <p>Deliberately <b>no</b> {@code %}/{@code _}/{@code \} escaping: the pre-fix queries let
 * those act as LIKE wildcards, and changing that is a contract decision (out of scope).
 */
final class LikePatterns {

    private LikePatterns() {
    }

    /** {@code null} stays {@code null}; otherwise {@code prefix%}. */
    static String prefix(String prefix) {
        return prefix == null ? null : prefix + "%";
    }

    /** {@code null} stays {@code null}; otherwise lower-cased {@code %q%}. */
    static String lowerContains(String q) {
        return q == null ? null : "%" + q.toLowerCase(Locale.ROOT) + "%";
    }
}
