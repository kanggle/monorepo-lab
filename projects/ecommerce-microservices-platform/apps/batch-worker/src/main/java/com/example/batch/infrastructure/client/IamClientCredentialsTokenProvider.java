package com.example.batch.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Obtains and caches a GAP {@code client_credentials} access token (JWT) for
 * authenticating batch-worker's outbound internal calls with
 * {@code Authorization: Bearer} (TASK-BE-413 / AC-2).
 *
 * <p>Mirrors product-service {@link com.example.product.infrastructure.client.IamClientCredentialsTokenProvider}
 * (BE-402): plain {@link RestClient} + Jackson, cached token, {@code REFRESH_SKEW} before
 * expiry, lazy acquisition. Startup is NOT coupled to IAM availability — a token-acquisition
 * failure surfaces as a {@code FAILED} batch run, not a startup error.
 *
 * <p>Config keys: {@code iam.internal-client.token-uri},
 * {@code iam.internal-client.client-id} (default {@code ecommerce-internal-services-client}),
 * {@code iam.internal-client.client-secret}.
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
            @Value("${iam.internal-client.client-id:ecommerce-internal-services-client}") String clientId,
            @Value("${iam.internal-client.client-secret:secret}") String clientSecret) {
        this.tokenUri = tokenUri;
        // W-1: RFC 7617 requires UTF-8 for the Basic auth credentials
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        // W-2: explicit connect+read timeouts — a hung IAM endpoint would otherwise block
        // synchronized currentBearer() for the entire ShedLock window. IAM token calls are
        // lightweight so 5s/5s is sufficient.
        this.restClient = RestClients.timed(Duration.ofSeconds(5), Duration.ofSeconds(5))
                .build();
    }

    /**
     * @return a currently-valid bearer access token, fetching/refreshing from IAM as needed.
     * @throws org.springframework.web.client.RestClientException if token acquisition fails
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
            throw new IllegalStateException("IAM token endpoint returned no access_token");
        }
        long expiresIn = resp.expiresIn() != null ? resp.expiresIn() : 300L;
        this.cached = new CachedToken(resp.accessToken(), now.plusSeconds(expiresIn));
        log.debug("Obtained IAM client_credentials token (expires in {}s)", expiresIn);
        return resp.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType) {}
}
