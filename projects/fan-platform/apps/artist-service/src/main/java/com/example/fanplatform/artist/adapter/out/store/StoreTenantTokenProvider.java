package com.example.fanplatform.artist.adapter.out.store;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * TASK-MONO-759 — a bearer whose {@code tenant_id} is the store tenant ({@code ecommerce}).
 *
 * <p>artist-service's own {@code client_credentials} token carries {@code tenant_id =
 * fan-platform}; the store side admits only {@code tenant_id == ecommerce}. ADR-MONO-076
 * decided how a workload crosses that line: exchange its own token (RFC 8693 assume-tenant)
 * for one minted <em>for</em> the target tenant, gated at the issuer by
 * {@code WorkloadTenantCatalog} ({@code artist-service-client → {ecommerce}} only).
 *
 * <h2>A copy, deliberately</h2>
 *
 * This is ecommerce product-service's {@code TenantScopedIamTokenProvider} (TASK-MONO-721),
 * narrowed to one fixed tenant. That class says "if a second service needs it, that is the
 * moment to have that conversation" — and the conversation is an ADR: repo-root {@code libs/}
 * must stay project-agnostic and promoting a class there is an architecture decision
 * (CLAUDE.md § Shared vs project boundary), and a project-scoped {@code libs/} module cannot
 * serve two projects. So this ticket copies; promotion is left to whoever has a third caller.
 *
 * <h2>Failure shape</h2>
 *
 * Any failure (token endpoint down, {@code invalid_grant} because the catalog refuses the
 * tenant, an empty response) throws. The caller ({@link HttpStoreSellerDirectory}) turns every
 * throw into «cannot verify» — never into an empty bearer, which would only move the refusal
 * one service away from the decision that caused it. Failures are not cached.
 */
public class StoreTenantTokenProvider {

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    /** Re-fetch this long before {@code expires_in} elapses, so a call never races the clock. */
    private static final Duration EXPIRY_SKEW = Duration.ofSeconds(30);

    private final IamClientCredentialsTokenProvider baseTokenProvider;
    private final RestClient tokenClient;
    private final String tokenUri;
    private final String basicAuth;
    private final String scope;
    private final String tenantId;

    private volatile CachedToken cached;

    public StoreTenantTokenProvider(IamClientCredentialsTokenProvider baseTokenProvider,
                                    RestClient tokenClient,
                                    String tokenUri,
                                    String clientId,
                                    String clientSecret,
                                    String scope,
                                    String tenantId) {
        this.baseTokenProvider = Objects.requireNonNull(baseTokenProvider, "baseTokenProvider");
        this.tokenClient = Objects.requireNonNull(tokenClient, "tokenClient");
        this.tokenUri = Objects.requireNonNull(tokenUri, "tokenUri");
        this.basicAuth = "Basic " + Base64.getEncoder().encodeToString(
                (Objects.requireNonNull(clientId, "clientId") + ":"
                        + Objects.requireNonNull(clientSecret, "clientSecret"))
                        .getBytes(StandardCharsets.UTF_8));
        this.scope = scope;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    }

    /** A bearer valid for the store tenant. Throws on any failure (see class javadoc). */
    public synchronized String bearer() {
        CachedToken c = cached;
        if (c != null && Instant.now().plus(EXPIRY_SKEW).isBefore(c.expiresAt())) {
            return c.value();
        }
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

        TokenResponse response = tokenClient.post()
                .uri(tokenUri)
                .header("Authorization", basicAuth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException(
                    "assume-tenant exchange returned no access_token for tenant=" + tenantId);
        }
        long expiresIn = response.expiresIn() != null ? Math.max(response.expiresIn(), 0L) : 0L;
        cached = new CachedToken(response.accessToken(), Instant.now().plusSeconds(expiresIn));
        return response.accessToken();
    }

    private record CachedToken(String value, Instant expiresAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") Long expiresIn) {
    }
}
