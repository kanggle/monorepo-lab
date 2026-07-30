package com.kanggle.platformconsole.bff.domain.composition;

import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

/**
 * Signals that a composition leg was <b>not executed</b> because the per-leg
 * circuit breaker for its {@code (domain, route)} key is OPEN
 * (architecture.md § Resilience D5.A; {@code console-integration-contract.md}
 * §§ 2.4.9 / 2.4.9.1 / 2.4.9.2).
 *
 * <p><b>Why a domain exception and not {@code CallNotPermittedException}</b>:
 * the leg error classifiers that branch on this outcome live in the
 * <em>application</em> layer, and the domain layer is framework-free
 * ({@code platform/architecture-decision-rule.md} — Hexagonal). The
 * Resilience4j type is translated at the adapter boundary
 * ({@code adapter.outbound.resilience.Resilience4jLegResilienceAdapter}) so no
 * layer above infrastructure imports {@code io.github.resilience4j}.
 *
 * <p>Carries no cause message beyond the key: the breaker rejection itself is
 * not diagnostic information about the downstream (nothing was sent), and the
 * originating failures were already attributed by
 * {@code bff_fanout_errors{domain,route,code}} when they happened.
 *
 * @see LegOutcome#circuitOpen(DomainTarget)
 */
public class CircuitOpenException extends RuntimeException {

    private final transient DomainTarget domain;
    private final String route;

    public CircuitOpenException(DomainTarget domain, String route, Throwable cause) {
        super("Circuit OPEN for leg (" + domain + ", " + route + ") — call not permitted, "
                + "no outbound request was made", cause);
        this.domain = domain;
        this.route = route;
    }

    public DomainTarget domain() {
        return domain;
    }

    public String route() {
        return route;
    }
}
