package com.wms.outbound.application.port.out;

import com.wms.outbound.application.security.CallerScope;

/**
 * Out-port resolving the {@link CallerScope} of the in-flight request from the
 * security context (TASK-MONO-304 / ADR-MONO-022 § D9).
 *
 * <p>The application layer depends only on this port; the security-aware
 * implementation lives in {@code adapter.out.security}. Internal flows with no
 * security context (Kafka consumers, schedulers) resolve to
 * {@link CallerScope#unrestricted()} so their behaviour is unchanged.
 */
public interface CallerScopeProvider {

    CallerScope current();
}
