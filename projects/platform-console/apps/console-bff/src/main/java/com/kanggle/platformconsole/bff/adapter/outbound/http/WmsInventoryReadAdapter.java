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
 * wms inventory snapshot outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/v1/admin/dashboard/inventory} (snapshot
 *       page; wms read-model)</li>
 *   <li>Credential: GAP OIDC access token (per § 2.4.9.1 row 2 / § 2.4.5
 *       verbatim) — resolved by the caller via
 *       {@link com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort#selectFor(DomainTarget)}.</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A).</li>
 * </ul>
 */
@Component
public class WmsInventoryReadAdapter implements OperatorOverviewCompositionUseCase.WmsInventoryReadPort {

    private final RestClient client;

    public WmsInventoryReadAdapter(@Qualifier("wmsRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.WMS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> read(String tenantId, String credential) {
        return (Map<String, Object>) client.get()
                .uri("/api/v1/admin/dashboard/inventory")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + credential)
                .header("X-Tenant-Id", tenantId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
    }
}
