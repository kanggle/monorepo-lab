package com.kanggle.platformconsole.bff.application.composition;

import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

/**
 * Per-use-case error classification strategy injected into
 * {@link CompositionEngine#time(DomainTarget, java.util.function.Supplier, LegErrorClassifier)}.
 *
 * <p>Operator Overview and Domain Health share the fan-out skeleton (executor,
 * timeout, latency timer site, fixed leg order) but differ on how
 * exceptions thrown inside a leg map to a {@link CompositionLeg}:
 *
 * <ul>
 *   <li>Operator Overview (credential-aware data legs) — distinguishes
 *       {@code MissingCredentialException} (→ {@code forbidden /
 *       MISSING_PREREQUISITE}), {@code 401} (→ unauthorized flag for
 *       cross-leg collapse), {@code 403} (→ {@code forbidden} with
 *       {@code TENANT_FORBIDDEN}/{@code PERMISSION_DENIED} classified
 *       from the producer body), generic {@code 4xx/5xx} (→ {@code degraded
 *       / DOWNSTREAM_ERROR}), and socket timeout (→ {@code degraded /
 *       TIMEOUT}).</li>
 *   <li>Domain Health (credential-less actuator legs) — collapses every
 *       {@code HttpClientErrorException} to {@code degraded / DOWNSTREAM_ERROR}
 *       (no permission outcome on a public actuator leg), and treats
 *       socket timeout identically.</li>
 * </ul>
 *
 * <p>Both classifiers MUST emit the per-leg
 * {@code bff_fanout_errors{domain,route,code}} counter with the
 * historically-fixed {@code code} tag values so Prometheus dashboards
 * remain byte-equal across the refactor.
 */
@FunctionalInterface
public interface LegErrorClassifier {

    /**
     * Maps an exception thrown by a leg's body into a {@link CompositionLeg}.
     * Implementations MUST emit the appropriate
     * {@code bff_fanout_errors{...}} counter via the
     * {@link CompositionEngine#emitErrorCounter(DomainTarget, String)}
     * helper before returning. The {@code time()} caller has already
     * stopped the latency {@link io.micrometer.core.instrument.Timer.Sample}
     * before delegating here, so implementations need not touch the timer.
     *
     * @param domain the leg's domain target
     * @param error  the exception thrown inside the leg body
     * @return the {@link CompositionLeg} to surface for this leg
     */
    CompositionLeg classify(DomainTarget domain, Throwable error);
}
