package com.kanggle.platformconsole.bff.support;

import com.kanggle.platformconsole.bff.application.port.outbound.LegResiliencePort;
import com.kanggle.platformconsole.bff.domain.composition.CircuitOpenException;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Test doubles for {@link LegResiliencePort} (TASK-PC-BE-015).
 *
 * <p>{@link LegResiliencePort#execute} is a <b>generic method</b>, which Java
 * does not allow a lambda to implement, so the doubles are named classes rather
 * than inline lambdas.
 *
 * <p>The real gate's behaviour is exercised against Resilience4j itself in
 * {@code Resilience4jLegResilienceAdapterTest} and end-to-end through the booted
 * application in {@code CircuitBreakerIntegrationTest}. These doubles exist only
 * so tests <em>about something else</em> (fan-out ordering, leg classification,
 * trace propagation) are not silently also testing the breaker.
 */
public final class LegResilienceDoubles {

    private LegResilienceDoubles() {
    }

    /** A gate that always executes the leg body — the CLOSED-breaker behaviour. */
    public static LegResiliencePort passThrough() {
        return new PassThrough();
    }

    /**
     * A gate that rejects the named {@code (domain, route)} keys with
     * {@link CircuitOpenException} <b>without invoking the body</b>, and passes
     * everything else through. Models an already-OPEN breaker without having to
     * drive real failures through Resilience4j's sliding window.
     */
    public static LegResiliencePort openFor(DomainTarget... openDomains) {
        return new OpenFor(Set.of(openDomains));
    }

    private static final class PassThrough implements LegResiliencePort {
        @Override
        public <T> T execute(DomainTarget domain, String route, Supplier<T> call) {
            return call.get();
        }
    }

    private static final class OpenFor implements LegResiliencePort {
        private final Set<DomainTarget> open;

        private OpenFor(Set<DomainTarget> open) {
            this.open = open;
        }

        @Override
        public <T> T execute(DomainTarget domain, String route, Supplier<T> call) {
            if (open.contains(domain)) {
                throw new CircuitOpenException(domain, route, null);
            }
            return call.get();
        }
    }
}
