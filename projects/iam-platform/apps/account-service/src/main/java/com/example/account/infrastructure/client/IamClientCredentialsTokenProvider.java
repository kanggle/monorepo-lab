package com.example.account.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Obtains and caches a GAP {@code client_credentials} access token (JWT) for authenticating
 * account-service's outbound {@code /internal/auth/**} calls with {@code Authorization: Bearer}
 * (TASK-BE-487, ADR-005 단계 4). Used by {@link AuthServiceClient} to authenticate credential
 * create / identity-backfill requests into auth-service, which flipped its {@code /internal/auth/**}
 * receiver from {@code permitAll()} to a JWT requirement in the same change.
 *
 * <p>Hand-rolled rather than {@code spring-boot-starter-oauth2-client} on purpose: that starter pulls
 * in OAuth2 client autoconfiguration that would perturb account-service's own Spring Security chain
 * (which already runs an {@code oauth2ResourceServer} for its inbound {@code /internal/**}). This
 * provider uses only a plain {@link RestClient} + Jackson — no new dependency, no new
 * autoconfiguration (parity with the admin-service / auth-service blueprints, TASK-BE-318).
 *
 * <p>The token is cached and reused until {@code REFRESH_SKEW} before expiry, then re-fetched. Token
 * acquisition is lazy (first outbound auth call), so application startup is not coupled to GAP
 * availability.
 */
@Slf4j
@Component
public class IamClientCredentialsTokenProvider {

    /** Refresh the cached token this long before its actual expiry. */
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String tokenUri;
    private final String basicAuthHeader;

    private volatile CachedToken cached;

    public IamClientCredentialsTokenProvider(
            @Value("${iam.internal-client.token-uri:http://localhost:8081/oauth2/token}") String tokenUri,
            @Value("${iam.internal-client.client-id:account-service-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
        this.restClient = RestClient.create();
    }

    /**
     * @return a currently-valid bearer access token, fetching/refreshing from GAP as needed.
     * @throws org.springframework.web.client.RestClientException if token acquisition fails
     *         (no silent fallback — the caller's resilience policy handles the failure).
     */
    public synchronized String currentBearer() {
        Instant now = Instant.now();
        CachedToken c = this.cached;
        if (c != null && c.expiresAt.isAfter(now.plus(REFRESH_SKEW))) {
            return c.accessToken;
        }
        TokenResponse resp = restClient.post()
                .uri(tokenUri)
                .header("Authorization", basicAuthHeader)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);
        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            throw new IllegalStateException("GAP token endpoint returned no access_token");
        }
        long expiresIn = resp.expiresIn() != null ? resp.expiresIn() : 300L;
        this.cached = new CachedToken(resp.accessToken(), now.plusSeconds(expiresIn));
        log.debug("Obtained GAP client_credentials token (expires in {}s)", expiresIn);
        return resp.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType) {}
}
