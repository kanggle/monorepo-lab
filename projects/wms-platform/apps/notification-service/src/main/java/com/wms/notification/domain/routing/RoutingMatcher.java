package com.wms.notification.domain.routing;

import com.wms.notification.domain.alert.AlertEnvelope;
import com.wms.notification.domain.alert.AlertSeverity;
import java.util.List;
import java.util.Objects;

/**
 * Sealed predicate over an {@link AlertEnvelope}. Three v1 implementations:
 *
 * <ul>
 *   <li>{@link AlwaysMatch} — passes every event of the matching eventType.</li>
 *   <li>{@link PayloadPredicateMatch} — JSON-path-style payload check
 *       (subset: {@code GT}, {@code LT}, {@code EQ}, {@code ABS_GTE},
 *       {@code IN}). Sufficient for the seeded rules; extensible without
 *       domain churn.</li>
 *   <li>{@link SeverityThresholdMatch} — payload-derived severity passes
 *       when {@code >= min}.</li>
 * </ul>
 *
 * <p>JSON shape lives in {@code domain-model.md} § Value Objects. The JPA
 * adapter de/serialises via the {@code RoutingMatcherJsonCodec} helper.
 */
public sealed interface RoutingMatcher
        permits RoutingMatcher.AlwaysMatch,
                RoutingMatcher.PayloadPredicateMatch,
                RoutingMatcher.SeverityThresholdMatch {

    boolean matches(AlertEnvelope envelope);

    /** Always-matches singleton-style matcher. */
    record AlwaysMatch() implements RoutingMatcher {
        @Override
        public boolean matches(AlertEnvelope envelope) {
            return true;
        }
    }

    /** Comparison operator for {@link PayloadPredicateMatch}. */
    enum Op {
        /** Strict greater-than against a numeric payload field. */
        GT,
        /** Strict less-than against a numeric payload field. */
        LT,
        /** Equality (numeric or string). */
        EQ,
        /** {@code abs(value) >= threshold} — used for "delta over 100" style. */
        ABS_GTE,
        /** Payload value is contained in a provided list. */
        IN
    }

    /**
     * Payload predicate. {@code jsonPath} uses a tiny dotted-segment subset:
     * {@code $.payload.delta} → {@code payload → delta}. Array indexing not
     * supported in v1 (no seeded rule needs it).
     */
    record PayloadPredicateMatch(String jsonPath, Op op, Object value) implements RoutingMatcher {

        public PayloadPredicateMatch {
            Objects.requireNonNull(jsonPath, "jsonPath");
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(value, "value");
            if (jsonPath.isBlank()) {
                throw new IllegalArgumentException("jsonPath must not be blank");
            }
        }

        @Override
        public boolean matches(AlertEnvelope envelope) {
            Object lhs = JsonPathExtractor.extract(envelope, jsonPath);
            if (lhs == null) {
                return false;
            }
            return switch (op) {
                case GT -> compareNumeric(lhs, value) > 0;
                case LT -> compareNumeric(lhs, value) < 0;
                case EQ -> Objects.equals(stringify(lhs), stringify(value));
                case ABS_GTE -> Math.abs(toDouble(lhs)) >= toDouble(value);
                case IN -> {
                    if (!(value instanceof List<?> list)) {
                        yield false;
                    }
                    String lhsStr = stringify(lhs);
                    yield list.stream().map(PayloadPredicateMatch::stringify)
                            .anyMatch(s -> Objects.equals(s, lhsStr));
                }
            };
        }

        private static int compareNumeric(Object a, Object b) {
            return Double.compare(toDouble(a), toDouble(b));
        }

        private static double toDouble(Object o) {
            if (o instanceof Number n) {
                return n.doubleValue();
            }
            return Double.parseDouble(o.toString());
        }

        private static String stringify(Object o) {
            return o == null ? null : o.toString();
        }
    }

    /** Severity threshold matcher. */
    record SeverityThresholdMatch(AlertSeverity min) implements RoutingMatcher {

        public SeverityThresholdMatch {
            Objects.requireNonNull(min, "min");
        }

        @Override
        public boolean matches(AlertEnvelope envelope) {
            Object raw = envelope.payload().get("severity");
            if (raw == null) {
                return false;
            }
            try {
                return AlertSeverity.valueOf(raw.toString()).isAtLeast(min);
            } catch (IllegalArgumentException unknown) {
                return false;
            }
        }
    }
}
