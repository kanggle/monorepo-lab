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
 * scm inventory visibility outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/scm/inventory/visibility} (snapshot;
 *       cross-node inventory; gateway public read per § 2.4.6 / FE-008)</li>
 *   <li>Credential: GAP OIDC access token (per § 2.4.9.1 row 3 / § 2.4.6
 *       verbatim).</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A).</li>
 * </ul>
 *
 * <p>The producer surfaces a {@code meta.warning: "Not for procurement decisions
 * (S5)"} string per the scm inventory-visibility contract — the BFF passes
 * the {@code data} payload through unmodified; surfacing the S5 hint is the
 * FE concern (per § 2.4.9.1 row 3 and the FE-half spec).
 */
@Component
public class ScmInventoryReadAdapter implements OperatorOverviewCompositionUseCase.ScmInventoryReadPort {

    private final RestClient client;

    public ScmInventoryReadAdapter(@Qualifier("scmRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.SCM;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> read(String tenantId, String credential) {
        return (Map<String, Object>) client.get()
                .uri("/api/scm/inventory/visibility")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
