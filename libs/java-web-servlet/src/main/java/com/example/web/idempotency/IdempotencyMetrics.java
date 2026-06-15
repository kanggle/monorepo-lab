package com.example.web.idempotency;

/**
 * Optional metrics SPI for {@link IdempotencyKeyFilter} (ADR-MONO-038 I5).
 *
 * <p>Deliberately Micrometer-free — {@code libs/java-web-servlet} must not gain
 * a Micrometer dependency. A service that instruments idempotency (e.g.
 * {@code outbound-service}) implements this by wrapping its {@code MeterRegistry};
 * services that don't pass {@link #NO_OP}.
 */
public interface IdempotencyMetrics {

    /** Result tag value: cached response replayed. */
    String RESULT_HIT = "hit";
    /** Result tag value: first-time request, proceeded to the handler. */
    String RESULT_MISS = "miss";
    /** Result tag value: same-key/different-body (409) or lock-held (503). */
    String RESULT_CONFLICT = "conflict";

    /**
     * Records one completed lookup decision.
     *
     * @param result        one of {@link #RESULT_HIT} / {@link #RESULT_MISS} / {@link #RESULT_CONFLICT}
     * @param durationNanos elapsed time of the store lookup + lock-attempt phase, in nanoseconds
     */
    void recordLookup(String result, long durationNanos);

    /**
     * Records a backing-store failure (lookup / lock / put / release threw and
     * the filter fell open).
     */
    void recordStoreFailure();

    /** No-op metrics; the filter default when a service does not instrument. */
    IdempotencyMetrics NO_OP = new IdempotencyMetrics() {
        @Override
        public void recordLookup(String result, long durationNanos) {
            // no-op
        }

        @Override
        public void recordStoreFailure() {
            // no-op
        }
    };
}
