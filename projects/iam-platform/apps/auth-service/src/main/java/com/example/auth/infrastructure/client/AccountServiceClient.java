package com.example.auth.infrastructure.client;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.SignupEmailConflictException;
import com.example.auth.application.exception.SignupInvalidException;
import com.example.auth.application.exception.SignupNotPossibleException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountProfileResult;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.application.result.SocialSignupResult;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Internal HTTP client for account-service.
 *
 * <p>TASK-BE-063: the login hot path no longer calls account-service for credential
 * lookup — auth-service owns credentials locally. This client is now a
 * status-and-social-signup adapter only. Configured with timeouts (connect=3s,
 * read=5s), retry (2 retries, exponential backoff + jitter, no retry on 4xx),
 * and circuit breaker (50% failure rate / 10s sliding window).</p>
 */
@Slf4j
@Component
public class AccountServiceClient implements AccountServicePort {

    /** Property key for the account-service base URL. */
    static final String BASE_URL_PROPERTY = "auth.account-service.base-url";

    /**
     * TASK-BE-580: parses the {@code code} out of an error body. Deliberately a plain local
     * mapper rather than the injected application one — this reads a foreign service's error
     * contract, so it must not inherit serialization config that exists for our own payloads,
     * and adding a constructor parameter would ripple into every test that builds this client.
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Environment environment;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final IamClientCredentialsTokenProvider tokenProvider;

    /**
     * Cached {@link RestClient} keyed by base URL string. The base URL is resolved
     * from {@link Environment} on every call rather than captured at construction
     * time. This avoids stale URLs when integration tests share a Spring context
     * via {@code ContextCache} but each test class registers a different
     * {@link org.springframework.test.context.DynamicPropertySource} value
     * (TASK-MONO-046-1 Cluster C / TASK-MONO-044c-1 RC#2 residue).
     *
     * <p>Production runtimes use a single static base URL, so the cache only ever
     * holds one entry; the lookup-and-build cost is negligible.
     */
    private volatile String cachedBaseUrl;
    private volatile RestClient cachedRestClient;

    @Autowired
    public AccountServiceClient(
            Environment environment,
            @Value("${auth.account-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${auth.account-service.read-timeout-ms:5000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = environment;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountService");
        this.retry = ResilienceClientFactory.buildRetry("accountService");
        this.tokenProvider = tokenProvider;
    }

    /**
     * Test-only constructor that pins a single {@code baseUrl} without going through
     * {@link Environment}. Used by {@code AccountServiceClientUnitTest} which builds
     * the client outside of a Spring context.
     *
     * <p>Production code uses the {@link Environment}-based constructor and therefore
     * benefits from the lazy URL resolution that Cluster C of TASK-MONO-046-1 added.
     */
    public AccountServiceClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs,
                                IamClientCredentialsTokenProvider tokenProvider) {
        this.environment = null;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.circuitBreaker = ResilienceClientFactory.buildCircuitBreaker("accountService");
        this.retry = ResilienceClientFactory.buildRetry("accountService");
        this.tokenProvider = tokenProvider;
        this.cachedBaseUrl = baseUrl;
        this.cachedRestClient = ResilienceClientFactory.buildRestClient(
                baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Resolves the {@link RestClient} bound to the current value of
     * {@code auth.account-service.base-url}. If the property changes between
     * calls (e.g. integration test contexts), a fresh {@link RestClient} is
     * built; otherwise the cached instance is reused.
     */
    private RestClient restClient() {
        // Test-only constructor pinned a RestClient without an Environment — use it.
        if (environment == null) {
            RestClient pinned = this.cachedRestClient;
            if (pinned == null) {
                throw new IllegalStateException(
                        "Test-only AccountServiceClient created without baseUrl");
            }
            return pinned;
        }
        String baseUrl = environment.getProperty(BASE_URL_PROPERTY);
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Required property '" + BASE_URL_PROPERTY + "' is not set");
        }
        RestClient existing = this.cachedRestClient;
        if (existing != null && baseUrl.equals(this.cachedBaseUrl)) {
            return existing;
        }
        synchronized (this) {
            if (this.cachedRestClient != null && baseUrl.equals(this.cachedBaseUrl)) {
                return this.cachedRestClient;
            }
            RestClient fresh = ResilienceClientFactory.buildRestClient(
                    baseUrl, connectTimeoutMs, readTimeoutMs);
            this.cachedBaseUrl = baseUrl;
            this.cachedRestClient = fresh;
            return fresh;
        }
    }

    /**
     * Maps a 4xx response from account-service to an {@link HttpClientErrorException}
     * (404 → {@code NotFound}, other 4xx → generic client error). Shared by the
     * status and profile lookups, whose downstream handlers were byte-identical.
     */
    private static final RestClient.ResponseSpec.ErrorHandler MAP_4XX = (request, response) -> {
        if (response.getStatusCode().value() == 404) {
            throw HttpClientErrorException.create(
                    response.getStatusCode(), "Not Found",
                    response.getHeaders(), new byte[0], null);
        }
        throw HttpClientErrorException.create(
                response.getStatusCode(), "Client Error",
                response.getHeaders(), new byte[0], null);
    };

    /**
     * Wraps a downstream call in the shared resilience pipeline (retry, then circuit
     * breaker) and invokes it. Centralises the decorate-retry-then-circuit-breaker
     * boilerplate that every port method repeated verbatim.
     */
    private <T> T callResilient(Supplier<T> call) {
        return CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, call)).get();
    }

    /**
     * TASK-BE-507: set {@code X-Tenant-Id} when the tenant was resolved. A blank/null tenant
     * sends no header at all, which account-service reads as "pin to fan-platform" — exactly
     * the pre-BE-507 behaviour, so an unresolvable client cannot break signup.
     */
    private static void setTenantHeader(HttpHeaders headers, String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            headers.set("X-Tenant-Id", tenantId);
        }
    }

    /**
     * TASK-BE-470-fix-001: server-side proxy for the browser signup page. Calls the
     * <b>public</b> {@code POST /api/accounts/signup} (no bearer token) and maps the
     * account-api error contract to typed exceptions the {@code /signup} controller
     * renders. Not wrapped in the retry/circuit-breaker pipeline: signup is a
     * user-driven, non-idempotent write, so a blind retry could double-create; the
     * caller shows an inline error and the user retries deliberately.
     */
    @Override
    public void signup(String email, String password, String displayName, String tenantId) {
        Map<String, String> body = new java.util.HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (displayName != null && !displayName.isBlank()) {
            body.put("displayName", displayName);
        }
        try {
            restClient().post()
                    .uri("/api/accounts/signup")
                    // TASK-BE-507: the tenant of the OIDC client the user registered through.
                    // Omitted when unresolved — account-service then pins fan-platform (net-zero).
                    .headers(h -> setTenantHeader(h, tenantId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            classifySignupClientError(e);
            throw new IllegalStateException("unreachable", e);
        } catch (RuntimeException e) {
            log.error("Signup proxy failed: msg={} type={} cause={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    /**
     * TASK-BE-580 — splits account-service's signup 4xx into <b>retry helps</b> and
     * <b>retry cannot help</b>. Always throws.
     *
     * <h2>Why the status code alone is not enough</h2>
     * 🔴 {@code 409} carries two opposite meanings on this endpoint, measured from
     * account-service's {@code GlobalExceptionHandler}:
     * <ul>
     *   <li>{@code 409 ACCOUNT_ALREADY_EXISTS} — the visitor already has an account. "Log in
     *       instead" is correct advice.</li>
     *   <li>{@code 409 TENANT_SUSPENDED} — the tenant is suspended. Before TASK-BE-580 this
     *       was reported as <i>"이미 가입된 이메일입니다. 로그인해 주세요."</i>, which is false
     *       AND sends the visitor to a login that also fails. The ticket predicted a
     *       {@code 403} here; the handler actually returns {@code 409}, which is precisely
     *       how it stayed hidden — it landed in the branch that already looked correct.</li>
     * </ul>
     * So the discriminator is the body's {@code code}, not the status.
     *
     * <h2>Unclassified is not "permanent"</h2>
     * An empty / non-JSON / unrecognised body cannot be judged. The safe side of "cannot
     * judge" is the <b>pre-existing behaviour</b> (transient), because claiming permanence
     * wrongly tells a visitor to stop trying something that would have worked.
     */
    private void classifySignupClientError(HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        String code = extractErrorCode(e);

        // TASK-BE-580 AC-2: the body's `code` goes in the log. Before this, only the status was
        // logged, so the log could not tell TENANT_NOT_FOUND from any other 404 — which is why
        // the original defect had to be found by reading container logs by hand.
        // 🔵 The body carries no PII today; log the code ONLY, so widening it later cannot leak.
        log.warn("Signup proxy got client error {} code={} from account-service",
                e.getStatusCode(), code == null ? "<unparsed>" : code);

        // `code` is null when the body could not be read. Java's immutable Set.contains(null)
        // throws NPE rather than returning false, so the null check is load-bearing, not
        // defensive noise — without it every unparseable 4xx becomes an NPE.
        if (code != null && SIGNUP_PERMANENT_ERROR_CODES.contains(code)) {
            throw new SignupNotPossibleException(code,
                    "Signup cannot succeed for this tenant: " + code);
        }
        if (status == 409) {
            throw new SignupEmailConflictException("Email already registered");
        }
        if (status == 400 || status == 422) {
            throw new SignupInvalidException("Signup validation failed");
        }
        // 429 (rate limit) — genuinely transient — and any 4xx whose code we could not read.
        throw new AccountServiceUnavailableException("Signup temporarily unavailable", e);
    }

    /**
     * Error codes for which retrying the signup can never change the answer.
     *
     * <p>Enumerated from account-service's {@code GlobalExceptionHandler} (TASK-BE-580 AC-0),
     * not inferred: {@code TENANT_NOT_FOUND} is a {@code 404} and {@code TENANT_SUSPENDED} is
     * a {@code 409}. Both come from {@code ActiveTenantGuard}, the single predicate deciding
     * whether an account may be born in a tenant at all.
     *
     * <p>🔴 Deliberately a <b>code</b> allowlist rather than a status rule: a future 4xx that
     * this list has not seen falls through to the transient branch, which is the pre-580
     * behaviour. A status rule would classify unknown futures as permanent and tell visitors
     * to give up on failures nobody has looked at yet.
     */
    private static final Set<String> SIGNUP_PERMANENT_ERROR_CODES =
            Set.of("TENANT_NOT_FOUND", "TENANT_SUSPENDED");

    /**
     * Reads {@code code} out of account-service's error body, or {@code null} when it is
     * absent, empty, or not JSON. {@code null} means "cannot judge" — never "permanent".
     */
    private String extractErrorCode(HttpClientErrorException e) {
        try {
            String body = e.getResponseBodyAsString();
            if (body == null || body.isBlank()) {
                return null;
            }
            JsonNode code = objectMapper.readTree(body).get("code");
            return code != null && code.isTextual() && !code.asText().isBlank()
                    ? code.asText()
                    : null;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }

    @Override
    public Optional<AccountStatusLookupResult> getAccountStatus(String accountId) {
        try {
            return callResilient(() -> doGetStatus(accountId));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.warn("Account service status lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            // TASK-BE-273 Phase 1 diagnostic: surface root cause + nested cause chain so
            // the CI Linux 503 origin (ConnectException / UnknownHostException /
            // SocketTimeoutException / Resilience4j CallNotPermittedException) is visible
            // in CI logs. Pre-existing message preserved for backward log scraping.
            log.error("Account service status lookup failed after retries: msg={} type={} cause={} causeType={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    private Optional<AccountStatusLookupResult> doGetStatus(String accountId) {
        try {
            // account-service returns { accountId, status, statusChangedAt } — map the
            // "status" field onto our port's accountStatus slot.
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient().get()
                    .uri("/internal/accounts/{id}/status", accountId)
                    // TASK-BE-318c: authenticate via GAP client_credentials Bearer JWT
                    // (account /internal/** dual-allows JWT or X-Internal-Token, BE-317).
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, MAP_4XX)
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }
            String returnedId = (String) body.getOrDefault("accountId", accountId);
            String status = (String) body.get("status");
            if (status == null) {
                return Optional.empty();
            }
            return Optional.of(new AccountStatusLookupResult(returnedId, status));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @Override
    public SocialSignupResult socialSignup(String email, String provider,
                                            String providerUserId, String displayName,
                                            String tenantId) {
        try {
            return callResilient(() -> doSocialSignup(email, provider, providerUserId, displayName, tenantId));
        } catch (HttpClientErrorException e) {
            log.warn("Account service social-signup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AccountServiceUnavailableException("Account service social-signup failed", e);
        } catch (RuntimeException e) {
            // TASK-BE-273 Phase 1 diagnostic: surface root cause + nested cause chain.
            // This catch block was the source of the legacy OAuth-callback IT 503 on CI
            // Linux (Status expected:<200> but was:<503>). Logging the cause type +
            // message + full stack lets us decide between Phase 2 option B (network
            // isolation) and option C (in-process fake controller).
            log.error("Account service social-signup failed after retries: msg={} type={} cause={} causeType={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    private SocialSignupResult doSocialSignup(String email, String provider,
                                               String providerUserId, String displayName,
                                               String tenantId) {
        try {
            Map<String, String> requestBody = Map.of(
                    "email", email,
                    "provider", provider,
                    "providerUserId", providerUserId,
                    "displayName", displayName != null ? displayName : ""
            );

            return restClient().post()
                    .uri("/internal/accounts/social-signup")
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    // TASK-BE-507: X-Tenant-Id — the same client-derived tenant this login
                    // already stamps on the social-identity row and the token.
                    .headers(h -> {
                        h.setBearerAuth(tokenProvider.currentBearer());
                        setTenantHeader(h, tenantId);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(SocialSignupResult.class);
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @Override
    public Optional<AccountProfileResult> getAccountProfile(String accountId) {
        try {
            return callResilient(() -> doGetProfile(accountId));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.warn("Account service profile lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            return Optional.empty();
        } catch (RuntimeException e) {
            // TASK-BE-273 Phase 1 diagnostic: surface root cause + nested cause chain.
            log.error("Account service profile lookup failed after retries: msg={} type={} cause={} causeType={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<AccountProfileResult> doGetProfile(String accountId) {
        try {
            // account-service returns:
            //   { accountId, email, emailVerified, displayName, preferredUsername, locale,
            //     tenantId, tenantType }
            Map<String, Object> body = restClient().get()
                    .uri("/internal/accounts/{id}/profile", accountId)
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, MAP_4XX)
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }

            return Optional.of(new AccountProfileResult(
                    (String) body.getOrDefault("accountId", accountId),
                    (String) body.get("email"),
                    body.get("emailVerified") instanceof Boolean b ? b :
                            Boolean.parseBoolean(String.valueOf(body.get("emailVerified"))),
                    (String) body.get("displayName"),
                    (String) body.get("preferredUsername"),
                    (String) body.get("locale"),
                    (String) body.get("tenantId"),
                    (String) body.get("tenantType")
            ));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @Override
    public List<String> listEntitledDomains(String tenantId) {
        // TASK-BE-517: the platform wildcard '*' (SUPER_ADMIN) is not a real tenant — account-service's
        // TenantId rejects it with 400 VALIDATION_ERROR ("Invalid tenant_id: *"). SUPER_ADMIN has no
        // per-tenant entitlements (it passes every domain gate via the wildcard), so skip the lookup.
        // Making the call not only 400s but counts toward the shared accountService circuit breaker,
        // opening it and fail-softing OTHER tenants' lookups during the same token-minting window —
        // dropping their entitled_domains claim → spurious domain 403s (the finance-card RED).
        if ("*".equals(tenantId)) {
            return List.of();
        }
        try {
            return callResilient(() -> doListEntitledDomains(tenantId));
        } catch (HttpClientErrorException e) {
            // TASK-BE-324: any 4xx (incl. unstubbed-WireMock 404) is treated as an
            // account-service failure here; the caller (TenantClaimTokenCustomizer)
            // fail-softs and omits the entitled_domains claim.
            log.warn("Account service entitled-domains lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AccountServiceUnavailableException(
                    "Account service entitled-domains lookup failed", e);
        } catch (RuntimeException e) {
            log.error("Account service entitled-domains lookup failed after retries: "
                            + "msg={} type={} cause={} causeType={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    /**
     * TASK-BE-491 (ADR-MONO-047 § D6): repointed from
     * {@code GET /internal/tenant-domain-subscriptions?tenantId=} to the dedicated
     * {@code GET /internal/tenants/{tenantId}/entitled-domains}, which returns the
     * <b>effective</b> set — {@code ACTIVE subscriptions ∩ effectiveCeiling(tenant)}.
     *
     * <p>The old endpoint still exists and still returns the RAW ACTIVE rows; it backs the
     * console catalog and subscription management, which must keep seeing what is stored.
     * Only this token-issuance leg moves. The ceiling is applied once, at the account-service
     * source that owns {@code tenants} (hence {@code org_node}) — so
     * {@code TenantClaimTokenCustomizer} and {@code OperatorRoleDerivation} are byte-unchanged,
     * and {@code derive(E ∩ C) = derive(E) ∩ derive(C)} because ADR-035 derivation is
     * per-domain.
     *
     * <p>An ungrouped tenant ({@code org_node_id = NULL}) has an UNBOUNDED ceiling, so this
     * response is byte-identical to the old one for every pre-ADR-047 tenant (D7 net-zero).
     *
     * <p>Failure semantics are unchanged: any 4xx/5xx propagates to
     * {@link #listEntitledDomains(String)}, which fail-softs into
     * {@code AccountServiceUnavailableException}; the customizer then omits the claim and
     * the domain gateway 403s. A failure can never <i>widen</i> reach.
     */
    @SuppressWarnings("unchecked")
    private List<String> doListEntitledDomains(String tenantId) {
        try {
            // account-service returns { "tenantId": "...", "domainKeys": [ "wms", ... ] }
            Map<String, Object> body = restClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/tenants/{tenantId}/entitled-domains")
                            .build(tenantId))
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .body(Map.class);

            List<String> domainKeys = new ArrayList<>();
            if (body != null && body.get("domainKeys") instanceof List<?> keys) {
                for (Object key : keys) {
                    if (key instanceof String dk && !dk.isBlank()) {
                        domainKeys.add(dk);
                    }
                }
            }
            return domainKeys;
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @Override
    public List<String> listAccountRoles(String tenantId, String accountId) {
        // TASK-BE-517: see listEntitledDomains — '*' (SUPER_ADMIN wildcard) is not a real tenant;
        // account-service 400s on it and the failure trips the shared accountService circuit. Skip.
        if ("*".equals(tenantId)) {
            return List.of();
        }
        try {
            return callResilient(() -> doListAccountRoles(tenantId, accountId));
        } catch (HttpClientErrorException e) {
            // ADR-MONO-033 S2: any 4xx is treated as an account-service failure;
            // the caller (future TenantClaimTokenCustomizer roles leg) fail-softs
            // and omits the roles claim so token issuance is never blocked.
            log.warn("Account service roles lookup returned client error {}: {}",
                    e.getStatusCode(), e.getMessage());
            throw new AccountServiceUnavailableException(
                    "Account service roles lookup failed", e);
        } catch (RuntimeException e) {
            log.error("Account service roles lookup failed after retries: "
                            + "msg={} type={} cause={} causeType={}",
                    e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    @Override
    public Optional<String> getTenantType(String tenantId) {
        return tenantLookup("tenant-type", () -> doGetTenantType(tenantId));
    }

    @Override
    public Optional<TenantLookupResult> getTenant(String tenantId) {
        return tenantLookup("tenant", () -> doGetTenant(tenantId));
    }

    /**
     * The shared failure policy for the two {@code GET /internal/tenants/{tenantId}} readers.
     *
     * <p>Extracted by TASK-BE-581 when {@link #getTenant(String)} joined
     * {@link #getTenantType(String)} on the same endpoint. The policy below is the one
     * TASK-BE-407 established; it is stated once so the two callers cannot drift into
     * disagreeing about what a 403 means.</p>
     *
     * <ul>
     *   <li><b>404</b> → {@link Optional#empty()}. "No such tenant" is an <i>answer</i>,
     *       not an outage — the caller applies its own policy to it.</li>
     *   <li><b>Any NON-404 4xx</b> (401 / 403 / 429 / 422 …) → outage. Treating it as empty
     *       would let a caller fall back to the B2C default and silently misclassify the
     *       tenant — the exact bug class TASK-BE-407 fixes.</li>
     *   <li><b>5xx / circuit-open / timeout / IO</b> → outage.</li>
     * </ul>
     *
     * @param label short name of the lookup, used verbatim in the log + exception message
     */
    private <T> Optional<T> tenantLookup(String label, Supplier<Optional<T>> call) {
        try {
            return callResilient(call);
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            log.warn("Account service {} lookup returned client error {}: {}",
                    label, e.getStatusCode(), e.getMessage());
            throw new AccountServiceUnavailableException(
                    "Account service " + label + " lookup failed", e);
        } catch (RuntimeException e) {
            log.error("Account service {} lookup failed after retries: "
                            + "msg={} type={} cause={} causeType={}",
                    label, e.getMessage(), e.getClass().getName(),
                    e.getCause() == null ? "null" : e.getCause().getMessage(),
                    e.getCause() == null ? "null" : e.getCause().getClass().getName(), e);
            throw new AccountServiceUnavailableException("Account service is unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Optional<String> doGetTenantType(String tenantId) {
        try {
            // account-service returns
            //   { tenantId, displayName, tenantType, status, createdAt, updatedAt }
            // — we consume only the tenantType field (TASK-BE-407).
            Map<String, Object> body = restClient().get()
                    .uri("/internal/tenants/{tid}", tenantId)
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, MAP_4XX)
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }
            Object tenantType = body.get("tenantType");
            if (tenantType instanceof String tt && !tt.isBlank()) {
                return Optional.of(tt);
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    /**
     * TASK-BE-581: reads BOTH fields the browser signup surface needs from the same
     * {@code GET /internal/tenants/{tenantId}} response {@code doGetTenantType} already
     * calls. A 200 whose {@code status} is missing/blank yields empty — an unreadable
     * record must not be reported as a healthy tenant, because the caller's safe direction
     * is "cannot confirm signup is possible".
     */
    @SuppressWarnings("unchecked")
    private Optional<TenantLookupResult> doGetTenant(String tenantId) {
        try {
            // account-service returns
            //   { tenantId, displayName, tenantType, status, createdAt, updatedAt }
            Map<String, Object> body = restClient().get()
                    .uri("/internal/tenants/{tid}", tenantId)
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, MAP_4XX)
                    .body(Map.class);

            if (body == null) {
                return Optional.empty();
            }
            Object tenantType = body.get("tenantType");
            Object status = body.get("status");
            if (tenantType instanceof String tt && !tt.isBlank()
                    && status instanceof String st && !st.isBlank()) {
                return Optional.of(new TenantLookupResult(tt, st));
            }
            return Optional.empty();
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> doListAccountRoles(String tenantId, String accountId) {
        try {
            // account-service returns { "accountId", "tenantId", "roles": ["ROLE_A", ...] }
            Map<String, Object> body = restClient().get()
                    .uri("/internal/tenants/{tid}/accounts/{aid}/roles", tenantId, accountId)
                    // TASK-BE-318c: GAP client_credentials Bearer JWT.
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .body(Map.class);

            List<String> roles = new ArrayList<>();
            if (body != null && body.get("roles") instanceof List<?> rawRoles) {
                for (Object item : rawRoles) {
                    if (item instanceof String role && !role.isBlank()) {
                        roles.add(role);
                    }
                }
            }
            return roles;
        } catch (HttpClientErrorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RuntimeException("Account service communication error", e);
        }
    }
}
