package com.example.security.oauth2.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Obtains and caches an OAuth2 {@code client_credentials} access token (JWT) from an
 * authorization server's token endpoint, for authenticating outbound service-to-service calls
 * with {@code Authorization: Bearer} (ADR-MONO-058 § D6).
 *
 * <p>This is the canonical, already-fixed shape promoted from 7 near-identical per-service
 * copies (iam, ecommerce, fan) that had diverged in ways that mattered:
 *
 * <ul>
 *   <li><strong>UTF-8 Basic-auth encoding.</strong> RFC 7617 requires the {@code client_id:client_secret}
 *       credential pair to be encoded as UTF-8 before Base64, not the JVM platform-default charset.
 *       Some copies used {@code String.getBytes()} (platform-default) instead of
 *       {@code String.getBytes(StandardCharsets.UTF_8)} — a live defect, not a style nit, on any
 *       host whose default charset is not UTF-8. This class always uses
 *       {@link StandardCharsets#UTF_8} explicitly.</li>
 *   <li><strong>Explicit connect/read timeouts.</strong> Some copies used {@link RestClient#create()}
 *       with no timeout configured at all — a hung token endpoint would block the caller's thread
 *       indefinitely (e.g. for the entire duration of a scheduler lock window). Timeouts are
 *       required constructor parameters here; there is no zero-arg / default-timeout path that
 *       could silently reproduce that defect.</li>
 *   <li><strong>Parameterized {@code scope}.</strong> Some copies hardcoded one project's scope
 *       literal (e.g. {@code "internal.invoke"}) directly into the token request body. {@code scope}
 *       is a constructor parameter here — each consumer supplies its own registered scope(s), or
 *       omits it (a blank/{@code null} scope sends no {@code scope} parameter at all, matching the
 *       consumers that never registered one).</li>
 * </ul>
 *
 * <p>Hand-rolled rather than {@code spring-boot-starter-oauth2-client} on purpose (a decision every
 * promoted copy made independently, for the same reason): that starter pulls in
 * {@code spring-security-web} client autoconfiguration, which several adopting services avoid so it
 * does not perturb their own Spring Security chain. This class uses only a plain {@link RestClient}
 * + Jackson — no new autoconfiguration, no servlet/reactive dependency (this module,
 * {@code libs/java-security}, is framework-neutral; see {@code assertClasspathNeutrality} in this
 * module's build).
 *
 * <p>The token is cached and reused until {@code REFRESH_SKEW} before its actual expiry, then
 * re-fetched. Token acquisition is lazy — the first call to {@link #currentBearer()} — so a
 * consumer's application startup is never coupled to the authorization server's availability.
 *
 * <p>This class is a plain, framework-neutral POJO — it carries no Spring stereotype annotation and
 * is never itself a component-scanned bean (consistent with {@code AllowedIssuersValidator} /
 * {@code TenantClaimValidator} in this same module). Each consuming service constructs it directly,
 * typically from its own {@code @Configuration} class, supplying its own property-sourced values.
 *
 * <p><strong>Runtime requirement.</strong> Deserializing the token endpoint's JSON response relies
 * on Jackson being present on the consumer's runtime classpath ({@code jackson-databind} is an
 * {@code implementation} dependency of this module, so it is transitively available to every
 * consumer without any extra step).
 */
public class IamClientCredentialsTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(IamClientCredentialsTokenProvider.class);

    /** Refresh the cached token this long before its actual expiry. */
    private static final Duration REFRESH_SKEW = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String tokenUri;
    private final String basicAuthHeader;
    private final String tokenRequestBody;

    private volatile CachedToken cached;

    /**
     * @param tokenUri        the authorization server's {@code client_credentials} token endpoint
     *                         (e.g. {@code http://iam.local/oauth2/token}). Must not be {@code null}.
     * @param clientId         the registered OAuth2 client id. Must not be {@code null}.
     * @param clientSecret     the registered OAuth2 client secret. Must not be {@code null}. Encoded
     *                         as UTF-8 into the {@code Authorization: Basic} header per RFC 7617.
     * @param scope            the scope(s) to request, exactly as the authorization server expects
     *                         them in a single {@code scope} form parameter (e.g.
     *                         {@code "internal.invoke"}). A {@code null} or blank value omits the
     *                         {@code scope} parameter from the token request entirely, rather than
     *                         sending an empty one — some authorization servers reject a present but
     *                         empty {@code scope} parameter differently than an absent one.
     * @param connectTimeout   the connect timeout for the token request. Must be a positive
     *                         duration — a zero or negative duration is rejected, because
     *                         {@link SimpleClientHttpRequestFactory} treats {@link Duration#ZERO} as
     *                         "no timeout", which is the exact defect this constructor exists to
     *                         make impossible to reintroduce by omission.
     * @param readTimeout      the read timeout for the token request. Same positivity requirement
     *                         as {@code connectTimeout}.
     * @throws NullPointerException     if {@code tokenUri}, {@code clientId}, {@code clientSecret},
     *                                   {@code connectTimeout} or {@code readTimeout} is {@code null}.
     * @throws IllegalArgumentException if {@code connectTimeout} or {@code readTimeout} is zero or
     *                                   negative.
     */
    public IamClientCredentialsTokenProvider(
            String tokenUri,
            String clientId,
            String clientSecret,
            String scope,
            Duration connectTimeout,
            Duration readTimeout) {
        this.tokenUri = Objects.requireNonNull(tokenUri, "tokenUri");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");

        // RFC 7617: HTTP Basic credentials MUST be UTF-8 encoded before Base64 — NOT the JVM
        // platform-default charset (java.lang.String#getBytes() with no argument). This is the
        // exact defect ADR-MONO-058 § D6 promotes this class to close everywhere at once.
        this.basicAuthHeader = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        this.tokenRequestBody = (scope == null || scope.isBlank())
                ? "grant_type=client_credentials"
                : "grant_type=client_credentials&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be a positive duration (got " + duration
                    + "); a zero or negative duration disables the timeout entirely, which is the "
                    + "no-timeout defect this class exists to close (ADR-MONO-058 § D6)");
        }
    }

    /**
     * @return a currently-valid bearer access token, fetching/refreshing from the authorization
     *         server as needed.
     * @throws org.springframework.web.client.RestClientException if token acquisition fails (no
     *         silent fallback — the caller's own resilience/fail policy handles the failure).
     */
    public synchronized String currentBearer() {
        Instant now = Instant.now();
        CachedToken c = this.cached;
        if (c != null && c.expiresAt().isAfter(now.plus(REFRESH_SKEW))) {
            return c.accessToken();
        }
        TokenResponse resp = restClient.post()
                .uri(tokenUri)
                .header("Authorization", basicAuthHeader)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(tokenRequestBody)
                .retrieve()
                .body(TokenResponse.class);
        if (resp == null || resp.accessToken() == null || resp.accessToken().isBlank()) {
            throw new IllegalStateException("client_credentials token endpoint returned no access_token");
        }
        long expiresIn = resp.expiresIn() != null ? resp.expiresIn() : 300L;
        this.cached = new CachedToken(resp.accessToken(), now.plusSeconds(expiresIn));
        log.debug("Obtained client_credentials token (expires in {}s)", expiresIn);
        return resp.accessToken();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("token_type") String tokenType) {}
}
