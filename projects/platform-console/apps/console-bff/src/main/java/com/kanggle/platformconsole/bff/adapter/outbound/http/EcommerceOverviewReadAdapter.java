package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.EcommerceOverviewReadPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * ecommerce product catalog snapshot outbound leg for the Operator Overview
 * composition (§ 2.4.9.1 row 6, TASK-MONO-243 — ADR-MONO-030 Step 4 facet
 * a-후속-2).
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/admin/products?page=0&size=1} (page total
 *       snapshot; the producer body's {@code totalElements} is the tenant
 *       product count surfaced).</li>
 *   <li>Credential: IAM OIDC access token (per § 2.4.9.1 row 6 / the 6-row
 *       sealed credential selector — {@code ECOMMERCE → IamOidcAccessToken}).</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A); the
 *       ecommerce gateway re-injects the trusted header from the token.</li>
 * </ul>
 *
 * <p><b>Gateway-routed (not direct-to-producer).</b> Unlike the other 5 overview
 * legs, this leg goes <i>through</i> the ecommerce {@code gateway-service}
 * ({@code ecommerce.local}) because product-service is a header-trust service,
 * not a JWT resource server: the gateway validates the IAM OIDC token, enforces
 * {@code account_type=OPERATOR} for {@code /api/admin/**}, injects the trusted
 * {@code X-Tenant-Id}, and strips client headers; product-service reads only that
 * trusted header. This reuses the same {@code ecommerceRestClient} /
 * {@code ecommerce.local} path as the § 2.4.9.2 health leg.
 *
 * <p><b>Distinct from</b> {@link EcommerceHealthReadAdapter}: that adapter is the
 * credential-LESS {@code /actuator/health} probe; this is the credential-FULL
 * operator-plane read. Both share {@code ecommerceRestClient} but differ in path
 * and authorization.
 */
@Component
public class EcommerceOverviewReadAdapter implements EcommerceOverviewReadPort {

    private final RestClient client;

    public EcommerceOverviewReadAdapter(@Qualifier("ecommerceRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.ECOMMERCE;
    }

    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        return RestClientHelper.authenticatedGet(
                client,
                uriBuilder -> uriBuilder
                        .path("/api/admin/products")
                        .queryParam("page", 0)
                        .queryParam("size", 1)
                        .build(),
                tenantId,
                credential);
    }
}
