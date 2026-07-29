package com.kanggle.platformconsole.bff.application.port.outbound;

import com.kanggle.platformconsole.bff.domain.composition.CircuitOpenException;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

import java.util.function.Supplier;

/**
 * Outbound port for the <b>per-leg resilience gate</b> — the circuit-breaker +
 * bounded-retry envelope every composition fan-out leg is executed inside
 * (architecture.md § Resilience D5.A, {@code console-integration-contract.md}
 * §§ 2.4.9 / 2.4.9.1 / 2.4.9.2, {@code rules/traits/integration-heavy.md} I2/I3).
 *
 * <h2>Keying</h2>
 * The gate is keyed by the <b>pair</b> {@code (domain, route)}, verbatim from the
 * spec: <i>"Per-leg circuit-breaker keyed by {@code (domain, route)} — a wms
 * outage does not open the breaker for scm"</i>, and § 2.4.9.2's <i>"sibling
 * circuit instance to § 2.4.9.1's {@code (domain, "operator-overview")}
 * (independent state, so one dashboard's circuit trip does not bleed into the
 * other)"</i>. Domain alone is NOT the key: {@code wms} is a leg of both the
 * Operator Overview and the Domain Health dashboards, and the two must trip
 * independently.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>The gate is <b>transparent on success</b> — it returns whatever
 *       {@code call} returns.</li>
 *   <li>Exceptions thrown by {@code call} propagate <b>unchanged</b> after the
 *       retry budget is spent, so the caller's existing
 *       {@code LegErrorClassifier} chain keeps classifying
 *       {@code HttpClientErrorException} / {@code ResourceAccessException}
 *       exactly as before.</li>
 *   <li>When the breaker for the key is OPEN the call is <b>not executed at
 *       all</b> and a {@link CircuitOpenException} is thrown instead — no socket
 *       is opened, no per-leg timeout is paid.</li>
 * </ul>
 *
 * <p>The application layer depends on this interface only; the Resilience4j
 * implementation lives in
 * {@code adapter.outbound.resilience.Resilience4jLegResilienceAdapter}
 * ({@code rules/traits/integration-heavy.md} I7 — vendor primitives isolated in
 * an adapter).
 */
public interface LegResiliencePort {

    /**
     * Executes {@code call} behind the {@code (domain, route)} circuit breaker
     * and its bounded retry.
     *
     * @param domain the leg's domain target
     * @param route  the composition route label (e.g. {@code "operator-overview"},
     *               {@code "domain-health"}, {@code "notification-aggregator"});
     *               the second half of the breaker key
     * @param call   the leg body
     * @param <T>    the leg body's result type
     * @return the leg body's result
     * @throws CircuitOpenException if the breaker for this key is OPEN — the leg
     *                              body was NOT invoked
     */
    <T> T execute(DomainTarget domain, String route, Supplier<T> call);
}
