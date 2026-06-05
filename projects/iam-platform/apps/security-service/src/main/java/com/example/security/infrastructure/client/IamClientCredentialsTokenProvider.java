package com.example.security.infrastructure.client;

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
 * outbound {@code /internal/**} calls with {@code Authorization: Bearer} (TASK-BE-318, ADR-005 단계 3).
 *
 * <p>Hand-rolled rather than {@code spring-boot-starter-oauth2-client} on purpose: that starter pulls
 * in {@code spring-security-web}, which would activate Spring Boot's default (lock-everything) servlet
 * security chain on security-service — security-service intentionally has no Spring Security web chain
 * (TASK-BE-317 옵션 b). This provider uses only a plain {@link RestClient} + Jackson, so no new
 * autoconfiguration is introduced.
 *
 * <p>The token is cached and reused until {@code REFRESH_SKEW} before expiry, then re-fetched. Token
 * acquisition is lazy (first call), so application startup is not coupled to GAP availability.
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
            @Value("${iam.internal-client.client-id:security-service-client}") String clientId,
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
