package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: erp masterdata-service {@code /actuator/health} read
 * for the Domain Health composition (no gateway in v1).
 *
 * <p>Extracted from the historic
 * {@code DomainHealthCompositionUseCase.ErpHealthReadPort} nested interface
 * (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/}. Behavior unchanged.
 */
public interface ErpHealthReadPort {
    Map<String, Object> read();
}
