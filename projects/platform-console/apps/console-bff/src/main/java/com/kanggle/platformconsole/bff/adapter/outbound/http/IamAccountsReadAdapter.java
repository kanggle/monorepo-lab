package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.application.port.outbound.IamAccountsReadPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * GAP accounts summary outbound leg for the Operator Overview composition.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Endpoint: {@code GET /api/admin/accounts?page=0&size=1}
 *       (page total snapshot — {@code totalElements} is the count surfaced)</li>
 *   <li>Credential: RFC 8693 exchanged <b>operator</b> token (per
 *       {@code console-integration-contract.md} § 2.4.9.1 row 1 / § 2.6) —
 *       resolved by the caller via
 *       {@link com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort#selectFor(DomainTarget)}
 *       and passed verbatim as the {@code credential} parameter.</li>
 *   <li>Tenant: {@code X-Tenant-Id} header forwarded verbatim (D6.A
 *       producer-authoritative).</li>
 * </ul>
 *
 * <p>Errors are propagated as {@link org.springframework.web.client.RestClientException}
 * subtypes (notably {@link org.springframework.web.client.HttpClientErrorException}) —
 * the composition use-case maps them to {@code LegOutcome} per the
 * degrade-policy classification (D5.A).
 */
@Component
public class IamAccountsReadAdapter implements IamAccountsReadPort {

    private final RestClient client;

    public IamAccountsReadAdapter(@Qualifier("gapRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public DomainTarget domainTarget() {
        return DomainTarget.IAM;
    }

    @Override
    public Map<String, Object> read(String tenantId, String credential) {
        return RestClientHelper.authenticatedGet(
                client,
                uriBuilder -> uriBuilder
                        .path("/api/admin/accounts")
                        .queryParam("page", 0)
                        .queryParam("size", 1)
                        .build(),
                tenantId,
                credential);
    }
}
