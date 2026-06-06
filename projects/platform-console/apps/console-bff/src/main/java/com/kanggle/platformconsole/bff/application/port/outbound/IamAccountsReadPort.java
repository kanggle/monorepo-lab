package com.kanggle.platformconsole.bff.application.port.outbound;

import java.util.Map;

/**
 * Narrow outbound port: GAP accounts summary read for the Operator Overview
 * composition.
 *
 * <p>Extracted from the historic
 * {@code OperatorOverviewCompositionUseCase.IamAccountsReadPort} nested
 * interface (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/} per Hexagonal port-adapter discipline
 * (the use-case should depend on outbound ports, not own them as nested
 * types). Behavior unchanged — same supertype, same method shape.
 */
public interface IamAccountsReadPort extends DomainReadPort<Map<String, Object>> {
}
