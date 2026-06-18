package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calls auth-service internal endpoints to force-logout (revoke all sessions)
 * and — TASK-MONO-295 (ADR-MONO-040 Phase 2) — to resolve a credential's login
 * email from its {@code account_id}.
 * Contract: specs/contracts/http/internal/admin-to-auth.md
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestClient restClient;
    private final String internalToken;

    public AuthServiceClient(
            @Value("${admin.auth-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${admin.downstream.internal-token:}") String internalToken) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.internalToken = internalToken;
    }

    @Retry(name = "authService")
    @CircuitBreaker(name = "authService")
    public ForceLogoutResponse forceLogout(String accountId,
                                           String operatorId,
                                           String reason,
                                           String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("operatorId", operatorId);

        try {
            return restClient.post()
                    .uri("/internal/auth/accounts/{accountId}/force-logout", accountId)
                    .headers(h -> {
                        h.add("Idempotency-Key", idempotencyKey);
                        h.add("X-Operator-ID", operatorId);
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(ForceLogoutResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("auth-service force-logout returned {}: {}", e.getStatusCode(), e.getMessage());
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException("auth-service error " + e.getStatusCode().value(), e);
            }
            throw new DownstreamFailureException("auth-service error " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("auth-service force-logout failed", e);
            throw new DownstreamFailureException("auth-service unavailable", e);
        }
    }

    /**
     * TASK-MONO-295 (ADR-MONO-040 Phase 2) — resolve the operator's login email
     * from its {@code account_id} (= the validated SAS subject-token {@code sub}),
     * for the login-time operator-token exchange's DUAL-KEY operator resolution.
     *
     * <p><b>FAIL-SOFT</b> (deliberately the opposite of {@link #forceLogout}, which
     * fail-closes a mutation): this is only the <em>legacy email FALLBACK</em> key.
     * Any failure (auth-service down / circuit-open / timeout / non-2xx / IO) →
     * {@link Optional#empty()} so the exchange proceeds on the account_id
     * {@code oidcSubject} alone (which already resolves once the Phase-3 cross-DB
     * {@code oidc_subject} backfill lands, and works today for any account_id-keyed
     * row). The fail-closed operator-resolution INVARIANT is unchanged: if neither
     * key matches a row, the exchange still 401s — this method never relaxes that;
     * it only supplies (or fails to supply) the optional second key.
     *
     * <p>The email is {@code confidential} PII — never logged (only its presence).
     *
     * @param accountId the validated subject-token {@code sub}
     * @return the login email, or empty (no row / lookup failed → account_id-only)
     */
    @Retry(name = "authService")
    @CircuitBreaker(name = "authService")
    public Optional<String> resolveOperatorEmail(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        try {
            ResolveEmailResponse resp = restClient.get()
                    .uri("/internal/auth/credentials/{accountId}/email", accountId)
                    .headers(h -> {
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, r) -> {
                        throw HttpClientErrorException.create(
                                r.getStatusCode(), r.getStatusText(),
                                r.getHeaders(), r.getBody().readAllBytes(), null);
                    })
                    .body(ResolveEmailResponse.class);
            if (resp == null || resp.email() == null || resp.email().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(resp.email());
        } catch (RuntimeException e) {
            // FAIL-SOFT: the email is only the legacy fallback key. Do NOT propagate —
            // resolution proceeds on account_id alone (fail-closed if THAT also misses).
            // Never log the email (PII); the account_id is an opaque UUID (internal).
            log.warn("auth-service operator-email resolve failed (fail-soft → account_id-only "
                    + "resolution): {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — resolve an operator's
     * {@code account_id} from its login email (the REVERSE of
     * {@link #resolveOperatorEmail}), scoped by the operator's {@code tenantId}.
     * Backs the one-time {@code oidc_subject} email→account_id backfill.
     *
     * <p><b>POST + body</b>: the email is {@code confidential} PII and must not land
     * in a request URL / access log. {@code tenantId} scopes the lookup because
     * {@code credentials.email} is unique only per tenant.
     *
     * <p><b>FAIL-SOFT</b> (like {@link #resolveOperatorEmail}, the opposite of
     * {@link #forceLogout}): any failure (auth-service down / circuit-open / timeout
     * / non-2xx / IO) → {@link Optional#empty()}. The backfill then leaves that
     * operator's {@code oidc_subject} unchanged (it stays resolvable via the RETAINED
     * email fallback) and retries on a later run. The email is never logged (PII);
     * the account_id is an opaque UUID (internal).
     *
     * @param email    the operator's login email
     * @param tenantId the operator's tenant scope (admin_operators.tenant_id)
     * @return the resolved account_id, or empty (no match / ambiguous / lookup failed)
     */
    @Retry(name = "authService")
    @CircuitBreaker(name = "authService")
    public Optional<String> resolveOperatorAccountId(String email, String tenantId) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("tenantId", tenantId);
        try {
            ResolveAccountIdResponse resp = restClient.post()
                    .uri("/internal/auth/credentials/account-id-by-email")
                    .headers(h -> {
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, r) -> {
                        throw HttpClientErrorException.create(
                                r.getStatusCode(), r.getStatusText(),
                                r.getHeaders(), r.getBody().readAllBytes(), null);
                    })
                    .body(ResolveAccountIdResponse.class);
            if (resp == null || resp.accountId() == null || resp.accountId().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(resp.accountId());
        } catch (RuntimeException e) {
            // FAIL-SOFT: never propagate. The unresolved operator is left unchanged
            // and retried on a later run. Never log the email (PII).
            log.warn("auth-service operator-account-id resolve failed (fail-soft → operator left "
                    + "unchanged for retry): {}", e.toString());
            return Optional.empty();
        }
    }

    public record ForceLogoutResponse(
            String accountId,
            Integer revokedTokenCount,
            Instant revokedAt
    ) {}

    /**
     * TASK-MONO-298 — response of
     * {@code POST /internal/auth/credentials/account-id-by-email}.
     * {@code accountId} is {@code null} when no credential matches the
     * {@code (tenantId, email)} scope (fail-soft).
     */
    public record ResolveAccountIdResponse(
            String accountId
    ) {}

    /**
     * TASK-MONO-295 — response of {@code GET /internal/auth/credentials/{accountId}/email}.
     * {@code email} is {@code null} when no credential row exists for the accountId.
     */
    public record ResolveEmailResponse(
            String accountId,
            String email
    ) {}
}
