package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.DomainReadPort;
import com.kanggle.platformconsole.bff.application.usecase.OperatorOverviewCompositionUseCase;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * finance balance health outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/finance/accounts/{operatorDefaultAccountId}/balances}</li>
 *   <li>Credential: GAP OIDC access token (per § 2.4.9.1 row 4 / § 2.4.7
 *       verbatim).</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A).</li>
 * </ul>
 *
 * <p><b>Both paths first-class (§ 2.4.9.1 Implementation guidance Option (a)
 * activation, TASK-PC-FE-014):</b> the use-case routes to
 * {@link #readBalances(String, String, String)} when the operator's
 * {@code finance_default_account_id} was present in the GAP registry response
 * (Phase 1 — TASK-BE-304) and forwarded as the
 * {@code X-Finance-Default-Account-Id} header by console-web (Phase 2 —
 * TASK-PC-FE-014); otherwise the use-case short-circuits with
 * {@code forbidden / MISSING_PREREQUISITE} and never invokes this adapter
 * (honest UX for operators whose {@code admin_operators.finance_default_account_id}
 * column is {@code NULL} — the default post-V0028 migration).
 *
 * <p>The generic {@link #read(String, String)} method is retained for the
 * {@link DomainReadPort} contract conformance but throws — it is the
 * <b>marker that the active path is</b> {@link #readBalances(String, String, String)},
 * not direct port use.
 */
@Component
public class FinanceBalanceReadAdapter implements OperatorOverviewCompositionUseCase.FinanceBalanceReadPort {

    private final RestClient client;

    public FinanceBalanceReadAdapter(@Qualifier("financeRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.FINANCE;
    }

    /**
     * Unsupported on this adapter — finance balance lookup requires an explicit
     * {@code accountId}. The composition use-case ALWAYS routes through
     * {@link #readBalances(String, String, String)} on the Option (a)
     * happy path (TASK-PC-FE-014), and short-circuits to
     * {@code forbidden / MISSING_PREREQUISITE} without invoking this adapter
     * at all on the absent-id path. This method exists solely for
     * {@link DomainReadPort} contract conformance — it is the marker that
     * the active path is {@code readBalances}.
     */
    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        throw new UnsupportedOperationException(
                "FinanceBalanceReadAdapter requires an explicit operatorDefaultAccountId; "
                        + "use readBalances(tenantId, credential, accountId). "
                        + "The composition use-case renders forbidden/MISSING_PREREQUISITE "
                        + "when the id is absent and never reaches this adapter.");
    }

    /**
     * Reads the operator's default account balances — the active Option (a)
     * path activated by TASK-PC-FE-014. When the use-case supplies a
     * non-blank {@code accountId} (the operator's
     * {@code finance_default_account_id} from {@code admin_operators} via the
     * GAP registry round-trip — Phase 1 TASK-BE-304 — forwarded by console-web
     * as the {@code X-Finance-Default-Account-Id} request header — Phase 2
     * TASK-PC-FE-014), this method fires the outbound HTTP call and returns
     * the balances payload.
     *
     * @param tenantId   forwarded verbatim (D6.A)
     * @param credential the GAP OIDC access token bearer value (per § 2.4.9.1 row 4)
     * @param accountId  the operator's default finance account id
     *                   (non-blank guaranteed by caller via
     *                   {@code StringUtils.hasText} gate in the use-case)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readBalances(String tenantId, String credential, String accountId) {
        return (Map<String, Object>) client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/finance/accounts/{id}/balances")
                        .build(accountId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
