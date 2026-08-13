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
 *
 * <p><b>ADR-MONO-064 § D1 gave this port a second job.</b> It was purely a guard —
 * every consumer asked it whether the caller may touch an <em>existing</em> order.
 * {@code ReceiveOrderService} now also reads it to decide what tenant to
 * <em>write</em> on a new one, because the create path is the one operation with no
 * prior order to guard, and leaving it tenant-less was what locked operators out of
 * everything they created.
 */
public interface CallerScopeProvider {

    CallerScope current();
}
