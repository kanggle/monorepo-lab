package com.kanggle.platformconsole.bff.application.port.outbound;

import com.kanggle.platformconsole.bff.domain.composition.LegOutcome;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;

/**
 * Outbound port: reads a domain's data for use in a composition route.
 *
 * <p>Composition use-cases depend only on this port — they never reach into HTTP
 * infrastructure directly. Adapters in {@code adapter.outbound.http} wire the
 * {@code RestClient} instances per domain.
 *
 * <p>The port is deliberately generic at skeleton level. Concrete typed read
 * operations will be defined per composition route (TASK-PC-FE-011 for the
 * first "Operator Overview" route).
 *
 * @param <R> the response data type for this domain read
 */
public interface DomainReadPort<R> {

    /**
     * Returns the target domain this port serves.
     */
    DomainTarget domainTarget();

    /**
     * Performs the read. If the domain leg fails, the implementation wraps
     * the error into a {@link LegOutcome#degraded} — never propagating raw
     * exceptions to the composition use-case (ADR-MONO-017 D5.A).
     *
     * @param tenantId the active tenant forwarded verbatim (D6.A)
     * @param credential the outbound credential bearer token value
     * @return the read result, or null on degraded/forbidden
     */
    R read(String tenantId, String credential);
}
