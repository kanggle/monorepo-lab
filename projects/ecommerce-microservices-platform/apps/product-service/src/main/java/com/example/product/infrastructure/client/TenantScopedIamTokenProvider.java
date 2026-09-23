package com.example.product.infrastructure.client;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) — obtains a bearer whose
 * {@code tenant_id} <b>is</b> the tenant this service is about to act on.
 *
 * <h3>Why two steps</h3>
 *
 * <p>{@code /internal/tenants/{tenantId}/**} treats {@code tenant_id == path tenant} as the
 * authorization decision itself. This service's credential is registered to
 * {@code global-account-platform} and provisions sellers into whatever tenant the request
 * names, so its own {@code client_credentials} token can never satisfy that rule — measured
 * 2026-09-23, {@code 403 TENANT_SCOPE_DENIED}, and it could not have returned anything else.
 *
 * <p>ADR-MONO-076 fixes that at issuance: exchange the workload token (RFC 8693) for one
 * minted for the target tenant. 🔴 <b>Nothing downstream changes</b> — the gateway filter and
 * account-service's tenant-scope guard keep reading the single {@code tenant_id} claim they
 * always read. This class is the whole caller-side cost of that choice.
 *
 * <h3>The refusal arrives here, not at the gateway</h3>
 *
 * <p>If this client is not permitted to assume the requested tenant, the <em>exchange</em>
 * fails with {@code invalid_grant} and no token is minted. So a provisioning attempt for a
 * tenant outside the grant never reaches account-service at all. That is deliberate
 * (ADR-MONO-076 § "Refused at the issuer, not at the edge") and it is why the caller sees a
 * token-acquisition failure rather than a 403.
 *
 * <h3>Caching, and why it is bounded the way it is</h3>
 *
 * <p>Exchanged tokens are cached per tenant and re-fetched before expiry with the same skew
 * the shared {@code client_credentials} provider uses. 🔴 The cache is keyed on the tenant, not
 * global: a token for tenant A is useless for tenant B and handing one over would be exactly
 * the cross-tenant defect this ticket exists to close.
 *
 * <p>🔵 It deliberately does not cache <em>failures</em>. A refusal is a decision about the
 * catalog, and the catalog changes by deployment — caching a "no" would keep refusing after
 * the grant was added, for a reason nobody could see from the outside.
 *
 * <h3>Why it lives in this service and not in {@code libs/java-security}</h3>
 *
 * <p>The shared library is repo-root and must stay project-agnostic. This class is thin and its
 * only consumer today is this service; promoting it is an ADR decision (CLAUDE.md § Shared vs
 * project boundary), not a side effect of this ticket. If a second service needs it, that is
 * the moment to have that conversation with a measurement in hand.
 */
@Slf4j
public class TenantScopedIamTokenProvider {

    private static final String TOKEN_EXCHANGE_GRANT =
            "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE =
            "urn:ietf:params:oauth:token-type:access_token";

    /** Re-fetch this long before {@code expires_in} elapses, so a call never races the clock. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);

    private final IamClientCredentialsTokenProvider baseTokenProvider;
    private final RestClient tokenClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    private final Map<String, CachedToken> byTenant = new ConcurrentHashMap<>();

    public TenantScopedIamTokenProvider(IamClientCredentialsTokenProvider baseTokenProvider,
                                        RestClient tokenClient,
                                        String tokenUri,
                                        String clientId,
                                        String clientSecret,
                                        String scope) {
        this.baseTokenProvider = baseTokenProvider;
        this.tokenClient = tokenClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
    }

    /**
     * A bearer valid for {@code tenantId}.
     *
     * @throws IllegalStateException when the exchange is refused or returns no token — the
     *         caller's fail-soft path turns that into "the seller stays PENDING", which is the
     *         same shape a network failure already had.
     */
    public String bearerFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("tenantId is required to obtain a tenant-scoped token");
        }
        CachedToken cached = byTenant.get(tenantId);
        if (cached != null && cached.isUsableAt(Instant.now())) {
            return cached.value();
        }
        CachedToken fresh = exchangeFor(tenantId);
        byTenant.put(tenantId, fresh);
        return fresh.value();
    }

    private CachedToken exchangeFor(String tenantId) {
        String subjectToken = baseTokenProvider.currentBearer();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", TOKEN_EXCHANGE_GRANT);
        form.add("subject_token", subjectToken);
        form.add("subject_token_type", ACCESS_TOKEN_TYPE);
        // RFC 8693 `audience` carries the selected tenant (auth-api.md / ADR-MONO-020 D2).
        form.add("audience", tenantId);
        if (scope != null && !scope.isBlank()) {
            form.add("scope", scope);
        }

        TokenResponse response;
        try {
            response = tokenClient.post()
                    .uri(tokenUri)
                    .header("Authorization", basicAuth())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RuntimeException e) {
            // 🔴 Includes the refusal case: a tenant this client may not assume comes back as
            // invalid_grant. Do NOT swallow it into an empty token — an empty bearer would
            // reach account-service and fail there instead, moving the diagnosis one service
            // away from the decision that caused it.
            throw new IllegalStateException(
                    "tenant-scoped token exchange failed for tenant=" + tenantId, e);
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException(
                    "tenant-scoped token exchange returned no access_token for tenant=" + tenantId);
        }

        long expiresIn = response.expiresIn() != null ? response.expiresIn() : 0L;
        Instant expiresAt = Instant.now().plusSeconds(Math.max(expiresIn, 0L));
        log.debug("obtained tenant-scoped IAM token for tenant={} (expires_in={}s)",
                tenantId, expiresIn);
        return new CachedToken(response.accessToken(), expiresAt);
    }

    private String basicAuth() {
        // RFC 7617: the credentials are UTF-8, not the platform default charset.
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private record CachedToken(String value, Instant expiresAt) {
        boolean isUsableAt(Instant now) {
            return expiresAt != null && now.plus(EXPIRY_SKEW).isBefore(expiresAt);
        }
    }

    /** Only the two fields this class reads; the issuer sends more and they are ignored. */
    record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn) {
    }
}
