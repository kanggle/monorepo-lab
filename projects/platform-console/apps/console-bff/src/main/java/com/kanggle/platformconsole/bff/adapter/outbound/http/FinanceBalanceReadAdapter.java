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
 * <p><b>MVP option (b) — {@code operatorDefaultAccountId} resolution
 * (§ 2.4.9.1 Implementation guidance):</b> finance v1 has no list/search GET
 * (per § 2.4.7), so the BFF cannot synthesize an account id. The required
 * {@code operatorDefaultAccountId} is supposed to come from a GAP
 * registry/operator-profile claim (option a, deferred to a separate spec-first
 * GAP enhancement). At MVP the BFF surfaces this prerequisite gap as an
 * honest {@code forbidden / MISSING_PREREQUISITE} card — the composition use
 * case detects the absent prerequisite <b>before</b> calling
 * {@link #read(String, String)} and never fires the outbound HTTP call.
 *
 * <p>This adapter is therefore reachable only when a future enhancement supplies
 * a non-blank account id; the use case threads it through as the
 * {@code accountId} parameter to {@link #readBalances(String, String, String)}.
 * The generic {@link #read(String, String)} method is retained for the
 * {@link DomainReadPort} contract but throws — callers MUST use the
 * id-aware overload.
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
     * {@code accountId}; the composition use-case routes through
     * {@link #readBalances(String, String, String)} after validating
     * MISSING_PREREQUISITE.
     */
    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        throw new UnsupportedOperationException(
                "FinanceBalanceReadAdapter requires an explicit operatorDefaultAccountId; "
                        + "use readBalances(tenantId, credential, accountId). "
                        + "MVP option (b) — the composition use case renders "
                        + "forbidden/MISSING_PREREQUISITE when the id is absent.");
    }

    /**
     * Reads the operator's default account balances.
     *
     * @param tenantId  forwarded verbatim (D6.A)
     * @param credential the GAP OIDC access token bearer value
     * @param accountId  the operator's default finance account id (non-blank guaranteed by caller)
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
