package com.example.web.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Per-service configuration for {@link IdempotencyKeyFilter} (ADR-MONO-038 I1) —
 * captures the divergences between the WMS services' filters as data rather than
 * code: which HTTP methods are guarded, which request paths the filter applies
 * to, the optional max key length, and the lock / entry TTLs.
 */
public final class IdempotencyFilterConfig {

    private final Set<String> methods;
    private final Predicate<String> pathPredicate;
    private final int maxKeyLength;
    private final Duration lockTtl;
    private final Duration entryTtl;

    private IdempotencyFilterConfig(Builder b) {
        this.methods = Set.copyOf(b.methods);
        this.pathPredicate = Objects.requireNonNull(b.pathPredicate, "pathPredicate");
        this.maxKeyLength = b.maxKeyLength;
        this.lockTtl = Objects.requireNonNull(b.lockTtl, "lockTtl");
        this.entryTtl = Objects.requireNonNull(b.entryTtl, "entryTtl");
        if (this.methods.isEmpty()) {
            throw new IllegalArgumentException("at least one applicable method is required");
        }
    }

    /**
     * True if the filter should apply to {@code request}: its method is guarded
     * and its request URI matches the path predicate.
     */
    public boolean shouldApply(HttpServletRequest request) {
        if (!methods.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return pathPredicate.test(request.getRequestURI());
    }

    /** Max Idempotency-Key length; {@code <= 0} means no length guard. */
    public int maxKeyLength() {
        return maxKeyLength;
    }

    public Duration lockTtl() {
        return lockTtl;
    }

    public Duration entryTtl() {
        return entryTtl;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder for {@link IdempotencyFilterConfig}. */
    public static final class Builder {
        private Set<String> methods = Set.of("POST");
        private Predicate<String> pathPredicate;
        private int maxKeyLength = 0;
        private Duration lockTtl = Duration.ofSeconds(30);
        private Duration entryTtl = Duration.ofHours(24);

        /** Guarded HTTP methods (case-insensitive; stored upper-cased). */
        public Builder methods(String... methods) {
            this.methods = upperCase(Set.of(methods));
            return this;
        }

        /** Guarded HTTP methods. */
        public Builder methods(Set<String> methods) {
            this.methods = upperCase(methods);
            return this;
        }

        /** Predicate over the request URI deciding whether the filter applies. */
        public Builder pathPredicate(Predicate<String> pathPredicate) {
            this.pathPredicate = pathPredicate;
            return this;
        }

        /** Convenience: apply to {@code apiPrefix*} but skip {@code webhookPrefix*}. */
        public Builder applyToPrefixSkippingWebhook(String apiPrefix, String webhookPrefix) {
            this.pathPredicate = uri -> uri != null
                    && !uri.startsWith(webhookPrefix)
                    && uri.startsWith(apiPrefix);
            return this;
        }

        /** Max key length; {@code <= 0} = no guard. */
        public Builder maxKeyLength(int maxKeyLength) {
            this.maxKeyLength = maxKeyLength;
            return this;
        }

        public Builder lockTtl(Duration lockTtl) {
            this.lockTtl = lockTtl;
            return this;
        }

        public Builder entryTtl(Duration entryTtl) {
            this.entryTtl = entryTtl;
            return this;
        }

        public IdempotencyFilterConfig build() {
            return new IdempotencyFilterConfig(this);
        }

        private static Set<String> upperCase(Set<String> in) {
            return in.stream().map(m -> m.toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
