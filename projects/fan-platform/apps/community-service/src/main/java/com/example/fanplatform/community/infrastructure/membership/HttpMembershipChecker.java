package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Real {@link MembershipChecker} that calls membership-service's internal
 * access-check endpoint over workload identity (ADR-MONO-005). Replaces the v1
 * {@code AlwaysAllowMembershipChecker} stub (wired by
 * {@code MembershipCheckerAutoConfig}).
 *
 * <p>Remote contract (membership-service architecture.md § Internal Access-Check
 * Contract — 1:1 with this port):
 * <pre>
 * GET {base-url}/internal/membership/access?accountId={}&amp;tier={}&amp;tenantId={}
 *   Authorization: Bearer &lt;community-service-client client_credentials JWT&gt;
 * → 200 { "allowed": &lt;boolean&gt; }
 * </pre>
 *
 * <p><strong>Fail-closed.</strong> Any failure — token acquisition error, connect
 * timeout, read timeout, connection refused, non-2xx, malformed/absent
 * {@code allowed} — yields {@code false} (deny). A domain "deny" is NOT an error:
 * the endpoint returns 200 {@code {allowed:false}}, which this adapter returns
 * verbatim. Author/operator short-circuit happens upstream in
 * {@code PostAccessGuard}, so the common author path never reaches here.
 */
@Slf4j
public class HttpMembershipChecker implements MembershipChecker {

    private final RestClient restClient;

    public HttpMembershipChecker(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean hasAccess(String accountId, String tier, String tenantId) {
        try {
            AccessResponse resp = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/membership/access")
                            .queryParam("accountId", accountId)
                            .queryParam("tier", tier)
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .body(AccessResponse.class);
            if (resp == null) {
                log.warn("membership-service returned empty body for access check "
                        + "(account={} tier={} tenant={}) → fail-closed deny",
                        accountId, tier, tenantId);
                return false;
            }
            return resp.allowed();
        } catch (Exception e) {
            // Fail-closed: any downstream/transport/auth error denies access.
            log.warn("membership-service access check failed "
                    + "(account={} tier={} tenant={}) → fail-closed deny: {}",
                    accountId, tier, tenantId, e.toString());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AccessResponse(boolean allowed) {}
}
