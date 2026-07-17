package com.example.auth.infrastructure.client;

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
 * auth-service's outbound {@code /internal/accounts/**} calls with {@code Authorization: Bearer}
 * (TASK-BE-318c, ADR-005 단계 3c). Used by {@link AccountServiceClient}.
 *
 * <p>auth-service <em>is</em> the GAP Spring Authorization Server, so this provider performs a
 * <strong>self-call</strong> to its own {@code /oauth2/token} endpoint to mint the token. Acquisition
 * is <strong>lazy</strong> (first outbound account call), so application startup is never coupled to
 * the SAS being ready — there is no startup self-dependency.
 *
 * <p>Hand-rolled rather than {@code spring-boot-starter-oauth2-client} on purpose: that starter pulls
 * in OAuth2 client autoconfiguration that would perturb auth-service's own Spring Authorization Server
 * / resource-server chains. This provider uses only a plain {@link RestClient} + Jackson — no new
 * dependency, no new autoconfiguration (parity with the security-service blueprint, TASK-BE-318).
 *
 * <p>The token is cached and reused until {@code REFRESH_SKEW} before expiry, then re-fetched.
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
            @Value("${iam.internal-client.client-id:auth-service-client}") String clientId,
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
                // Request the internal.invoke scope the receiver requires (TASK-BE-514/MONO-422).
                // SAS grants ONLY explicitly-requested scopes for client_credentials — omitting the
                // scope yields an empty scope claim → the /internal/** RequiredScopeValidator rejects
                // it (401). The client is registered with internal.invoke in auth-service V0019.
                .body("grant_type=client_credentials&scope=internal.invoke")
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
