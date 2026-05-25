package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: wms gateway-service {@code /actuator/health} read for
 * the Domain Health composition.
 *
 * <p>Extracted from the historic
 * {@code DomainHealthCompositionUseCase.WmsHealthReadPort} nested interface
 * (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/}. Behavior unchanged.
 */
public interface WmsHealthReadPort {
    Map<String, Object> read();
}
