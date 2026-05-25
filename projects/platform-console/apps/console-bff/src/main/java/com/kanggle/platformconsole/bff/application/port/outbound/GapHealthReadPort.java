package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: GAP gateway-service {@code /actuator/health} read for
 * the Domain Health composition.
 *
 * <p>Extracted from the historic
 * {@code DomainHealthCompositionUseCase.GapHealthReadPort} nested interface
 * (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/} per Hexagonal port-adapter discipline.
 * Behavior unchanged — same parameterless method signature.
 *
 * <p>Credential-less by design: actuator endpoints are public (§ 2.4.9.2
 * hard invariants); no tenant pass-through, no Authorization header.
 */
public interface GapHealthReadPort {
    Map<String, Object> read();
}
