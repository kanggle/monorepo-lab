package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * Real {@link ArtistAccountChecker} — calls artist-service's internal existence
 * endpoint over workload identity (ADR-MONO-005). TASK-FAN-BE-045 AC-6,
 * ADR-004 ACCEPTED — A.
 *
 * <p>Remote contract ({@code specs/contracts/http/artist-api.md} § Internal
 * artist-account existence check — 1:1 with this port):
 * <pre>
 * GET {base-url}/internal/artists/exists?accountId={}&amp;tenantId={}
 *   Authorization: Bearer &lt;community-service-client client_credentials JWT&gt;
 * → 200 { "exists": &lt;boolean&gt; }
 * </pre>
 *
 * <p><strong>Fail-closed.</strong> Any failure — token acquisition error, connect
 * timeout, read timeout, connection refused, non-2xx, malformed/absent
 * {@code exists} — yields {@code false}, which refuses the follow. That is the
 * intended behaviour and AC-6 asserts it explicitly: taking artist-service down
 * must not open follow. A domain "not an artist" is NOT an error — the endpoint
 * returns 200 {@code {exists:false}}, which this adapter returns verbatim.
 *
 * <p><strong>There is no always-allow sibling for this port</strong>, unlike
 * {@code MembershipChecker}. See {@code ArtistAccountCheckerConfig} for the
 * measurement behind that decision (TASK-FAN-BE-045 AC-7).
 */
@Slf4j
public class HttpArtistAccountChecker implements ArtistAccountChecker {

    private final RestClient restClient;

    public HttpArtistAccountChecker(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public boolean isArtistAccount(String accountId, String tenantId) {
        try {
            ExistsResponse resp = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/artists/exists")
                            .queryParam("accountId", accountId)
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .body(ExistsResponse.class);
            if (resp == null) {
                log.warn("artist-service returned empty body for artist-account check "
                                + "(account={} tenant={}) → fail-closed deny",
                        accountId, tenantId);
                return false;
            }
            return resp.exists();
        } catch (Exception e) {
            // Fail-closed: any downstream/transport/auth error refuses the follow.
            log.warn("artist-service artist-account check failed (account={} tenant={}) "
                            + "→ fail-closed deny: {}",
                    accountId, tenantId, e.toString());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExistsResponse(boolean exists) {}
}
