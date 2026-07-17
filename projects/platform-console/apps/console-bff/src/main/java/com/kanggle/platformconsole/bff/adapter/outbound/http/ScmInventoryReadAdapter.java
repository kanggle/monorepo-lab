package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.ScmInventoryReadPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * scm inventory visibility outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/inventory-visibility/snapshot} (cross-node
 *       inventory snapshot — scm {@code inventory-visibility-service}
 *       {@code InventoryVisibilityController}, the read producer named in
 *       § 2.4.6). The prior {@code /api/scm/inventory/visibility} path was a bug
 *       (TASK-MONO-162): it matched neither the scm gateway route
 *       {@code /api/v1/inventory-visibility/**} nor the service path
 *       {@code /api/inventory-visibility/snapshot}. The BFF calls the producer
 *       service path directly (no scm gateway in the federation cohort —
 *       consistent with the wms/finance/erp legs, all of which call their
 *       producer service directly). See the TASK-MONO-162 topology note.</li>
 *   <li>Credential: GAP OIDC access token (per § 2.4.9.1 row 3 / § 2.4.6
 *       verbatim).</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A). The scm
 *       producer resolves the tenant from the JWT {@code tenant_id} claim
 *       (TenantClaimExtractor), so the header is harmless surplus.</li>
 * </ul>
 *
 * <p>The producer surfaces a {@code meta.warning: "Not for procurement decisions
 * (S5)"} string per the scm inventory-visibility contract — the BFF passes
 * the {@code data} payload through unmodified; surfacing the S5 hint is the
 * FE concern (per § 2.4.9.1 row 3 and the FE-half spec).
 */
@Component
public class ScmInventoryReadAdapter implements ScmInventoryReadPort {

    private final RestClient client;

    public ScmInventoryReadAdapter(@Qualifier("scmRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        return RestClientHelper.authenticatedGet(
                client,
                "/api/inventory-visibility/snapshot",
                tenantId,
                credential);
    }
}
