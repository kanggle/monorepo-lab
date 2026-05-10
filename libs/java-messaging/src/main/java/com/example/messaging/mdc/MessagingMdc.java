package com.example.messaging.mdc;

import org.slf4j.MDC;

/**
 * MDC propagation helpers for messaging code paths.
 *
 * <p>All helpers return an {@link MdcCloseable} so callers use a try-with-resources
 * block. The closer restores any previous value of the affected MDC key (or removes
 * it if there was none), preventing leakage across listener invocations.
 *
 * <h2>Common keys</h2>
 * <ul>
 *   <li>{@code traceId}      — distributed trace identifier propagated from the envelope</li>
 *   <li>{@code eventId}      — unique event identifier (UUIDv7)</li>
 *   <li>{@code consumerLabel} — short label distinguishing the consumer pipeline
 *       (e.g. {@code "<service>-projection"} or {@code "<service>-saga"})</li>
 * </ul>
 *
 * <p>The helpers are defensive: a {@code null} or blank value is treated as a
 * no-op (the key is left untouched) so callers can write
 * {@code try (var ignored = MessagingMdc.withTraceId(envelope.traceId())) {…}}
 * without a guard.
 */
public final class MessagingMdc {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String EVENT_ID_KEY = "eventId";
    public static final String CONSUMER_LABEL_KEY = "consumerLabel";

    private MessagingMdc() {
    }

    /**
     * Push {@code traceId} onto MDC for the duration of the returned scope.
     */
    public static MdcCloseable withTraceId(String traceId) {
        return push(TRACE_ID_KEY, traceId);
    }

    /**
     * Push {@code eventId} onto MDC for the duration of the returned scope.
     */
    public static MdcCloseable withEventId(String eventId) {
        return push(EVENT_ID_KEY, eventId);
    }

    /**
     * Push {@code consumerLabel} onto MDC for the duration of the returned scope.
     */
    public static MdcCloseable withConsumerLabel(String consumerLabel) {
        return push(CONSUMER_LABEL_KEY, consumerLabel);
    }

    /**
     * Push the supplied envelope-derived context (trace + event ids) onto MDC.
     * Returns a single closer that restores both keys.
     */
    public static MdcCloseable withEnvelope(String traceId, String eventId) {
        MdcCloseable trace = withTraceId(traceId);
        MdcCloseable evt = withEventId(eventId);
        return () -> {
            try {
                evt.close();
            } finally {
                trace.close();
            }
        };
    }

    /**
     * Generic single-key push; package-private for tests + composing helpers.
     */
    static MdcCloseable push(String key, String value) {
        if (value == null || value.isBlank()) {
            return MdcCloseable.NOOP;
        }
        String previous = MDC.get(key);
        MDC.put(key, value);
        return () -> {
            if (previous == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, previous);
            }
        };
    }

    /**
     * AutoCloseable that restores the previous MDC value when closed. Defined
     * separately so {@code close()} can be invoked without a checked exception.
     */
    @FunctionalInterface
    public interface MdcCloseable extends AutoCloseable {

        /** No-op closer for blank inputs. */
        MdcCloseable NOOP = () -> { };

        @Override
        void close();
    }
}
