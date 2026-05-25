package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.ErpDepartmentsReadPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * erp masterdata snapshot outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/erp/masterdata/departments?active=true&page=0&size=1}
 *       (page total snapshot; {@code meta.totalElements} is the
 *       active department count surfaced)</li>
 *   <li>Credential: GAP OIDC access token (per § 2.4.9.1 row 5 / § 2.4.8
 *       verbatim).</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A).</li>
 * </ul>
 *
 * <p>{@code asOf=now} is implicit (erp E3 effective-dating — the query omits
 * {@code asOf} so the producer applies "now" semantics).
 */
@Component
public class ErpDepartmentsReadAdapter implements ErpDepartmentsReadPort {

    private final RestClient client;

    public ErpDepartmentsReadAdapter(@Qualifier("erpRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.ERP;
    }

    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        return RestClientHelper.authenticatedGet(
                client,
                uriBuilder -> uriBuilder
                        .path("/api/erp/masterdata/departments")
                        .queryParam("active", true)
                        .queryParam("page", 0)
                        .queryParam("size", 1)
                        .build(),
                tenantId,
                credential);
    }
}
