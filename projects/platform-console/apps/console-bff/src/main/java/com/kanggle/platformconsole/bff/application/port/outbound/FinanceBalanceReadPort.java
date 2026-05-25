package com.kanggle.platformconsole.bff.application.port.outbound;

import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Narrow outbound port: finance balance read for the Operator Overview
 * composition (operator default account id resolved upstream).
 *
 * <p>Extracted from the historic
 * {@code OperatorOverviewCompositionUseCase.FinanceBalanceReadPort} nested
 * interface (TASK-PC-BE-005 L4 Move Class) — relocated to
 * {@code application/port/outbound/} per Hexagonal port-adapter discipline.
 * Behavior unchanged — same supertype, same method signature.
 *
 * <p>The {@link #read(String, String)} inherited from {@link DomainReadPort}
 * remains for contract conformance but is the <b>marker for the inactive
 * path</b> — the finance leg never uses it (the adapter throws
 * {@link UnsupportedOperationException}). The active path is
 * {@link #readBalances(String, String, String)}, which the use-case invokes
 * once Option (a) is satisfied (TASK-PC-FE-014).
 */
public interface FinanceBalanceReadPort extends DomainReadPort<Map<String, Object>> {

    /**
     * TASK-PC-FE-014 Option (a) activation: reads the operator's default
     * finance account balances. The caller (use-case) guarantees
     * {@code accountId} is non-blank — the use-case applies
     * {@link StringUtils#hasText(CharSequence)} before invoking; blank or
     * null short-circuits to {@code MISSING_PREREQUISITE} on the use-case
     * side and this method is never reached. The {@link #read(String, String)}
     * default remains the {@link DomainReadPort} contract conformance
     * point but is {@code UnsupportedOperationException}-throwing on the
     * concrete adapter — the marker that the active path is
     * {@code readBalances}.
     *
     * @param tenantId   active tenant forwarded verbatim (D6.A)
     * @param credential GAP OIDC access token bearer value
     *                   (per § 2.4.9.1 row 4 / § 2.4.7)
     * @param accountId  operator's finance default account id (non-blank;
     *                   sourced from GAP registry
     *                   {@code productItem.operatorContext.defaultAccountId}
     *                   per TASK-BE-304, threaded through the
     *                   {@code X-Finance-Default-Account-Id} request header
     *                   per § 2.4.9.1 Option (a) activation)
     * @return the balances payload (domain-shaped {@code Map}); never
     *         {@code null}
     */
    Map<String, Object> readBalances(String tenantId, String credential, String accountId);
}
