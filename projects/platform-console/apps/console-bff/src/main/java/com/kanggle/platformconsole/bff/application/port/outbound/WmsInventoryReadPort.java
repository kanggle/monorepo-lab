package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: wms inventory snapshot read for the Operator Overview
 * composition.
 *
 * <p>Extracted from the historic
 * {@code OperatorOverviewCompositionUseCase.WmsInventoryReadPort} nested
 * interface (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/} per Hexagonal port-adapter discipline.
 * Behavior unchanged — same supertype, same method shape.
 */
public interface WmsInventoryReadPort extends DomainReadPort<Map<String, Object>> {
}
