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
 * and — TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — to resolve an operator's
 * {@code account_id} from its login email (the one-time {@code oidc_subject}
 * backfill).
 * Contract: specs/contracts/http/internal/admin-to-auth.md
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestClient restClient;
    // TASK-BE-487 (ADR-005 단계 4): auth-service /internal/auth/** flipped from permitAll to a GAP
    // client_credentials Bearer JWT requirement. This client now presents that token (client_id
    // admin-service-client, reusing the existing provider) instead of the static X-Internal-Token,
    // which auth-service never validated and has been removed.
    private final IamClientCredentialsTokenProvider tokenProvider;

    public AuthServiceClient(
            @Value("${admin.auth-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
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
        this.tokenProvider = tokenProvider;
    }

    /**
     * TASK-BE-467 — {@code tenantId} is the actor's resolved active tenant, stamped
     * as {@code X-Tenant-Id} for defense-in-depth tenant propagation. The actual
     * refresh_tokens tenant_id confinement at auth-service is the acknowledged
     * separate enforcement point (out of this task's scope); the header is a
     * forward-compatible, net-zero addition today ({@code "*"} = SUPER_ADMIN scope).
     */
    @Retry(name = "authService")
    @CircuitBreaker(name = "authService")
    public ForceLogoutResponse forceLogout(String accountId,
                                           String operatorId,
                                           String reason,
                                           String idempotencyKey,
                                           String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("operatorId", operatorId);

        try {
            return restClient.post()
                    .uri("/internal/auth/accounts/{accountId}/force-logout", accountId)
                    .headers(h -> {
                        h.add("Idempotency-Key", idempotencyKey);
                        h.add("X-Operator-ID", operatorId);
                        if (tenantId != null) h.add("X-Tenant-Id", tenantId);
                        // TASK-BE-487: GAP client_credentials Bearer JWT (auth /internal/auth/** is JWT-only).
                        h.setBearerAuth(tokenProvider.currentBearer());
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
     * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — resolve an operator's
     * {@code account_id} from its login email, scoped by the operator's
     * {@code tenantId}. Backs the one-time {@code oidc_subject} email→account_id
     * backfill.
     *
     * <p><b>POST + body</b>: the email is {@code confidential} PII and must not land
     * in a request URL / access log. {@code tenantId} scopes the lookup because
     * {@code credentials.email} is unique only per tenant.
     *
     * <p><b>FAIL-SOFT</b> (the opposite of
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
                        // TASK-BE-487: GAP client_credentials Bearer JWT (auth /internal/auth/** is JWT-only).
                        h.setBearerAuth(tokenProvider.currentBearer());
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
}
