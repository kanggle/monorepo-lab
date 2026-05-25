package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: finance account-service {@code /actuator/health} read
 * for the Domain Health composition (no gateway in v1).
 *
 * <p>Extracted from the historic
 * {@code DomainHealthCompositionUseCase.FinanceHealthReadPort} nested interface
 * (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/}. Behavior unchanged.
 */
public interface FinanceHealthReadPort {
    Map<String, Object> read();
}
