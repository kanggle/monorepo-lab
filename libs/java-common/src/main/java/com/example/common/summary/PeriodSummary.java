package com.example.common.summary;

/**
 * Calendar-period-to-date count summary shared across operator-overview surfaces.
 * <p>
 * Serialises to {@code {"today":n,"week":n,"month":n,"total":n}} — a cross-service
 * technical DTO reused as both the application result and the REST response for the
 * per-area {@code .../summary} endpoints (TASK-MONO-322 dedup of the six ecommerce
 * period-summary slices).
 * <ul>
 *   <li>{@code today}/{@code week}/{@code month} — rows created since the KST calendar
 *       period start (see {@code com.example.common.time.KstPeriodBounds}) through now</li>
 *   <li>{@code total} — cumulative tenant-scoped row count</li>
 * </ul>
 * All values are non-negative and satisfy {@code today <= week <= month <= total}
 * for a single tenant.
 */
public record PeriodSummary(long today, long week, long month, long total) {
}
