package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Read-only queries to security-service for the integrated audit view
 * (login_history, suspicious_events).
 *
 * <p>Wrapped by Resilience4j {@code @CircuitBreaker(name="securityService")}
 * and {@code @Retry(name="securityService")}. Per TASK-BE-033 the audit read
 * path is <strong>fail-fast</strong>: when the CB is OPEN or downstream returns
 * 5xx/timeouts, the client propagates {@link DownstreamFailureException} (mapped
 * to 503 by {@code AdminExceptionHandler}) rather than returning empty/stale
 * lists. This prevents the admin console from silently rendering a partial
 * audit view during a security-service outage.
 */
@Slf4j
@Component
public class SecurityServiceClient {

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;

    public SecurityServiceClient(
            @Value("${admin.security-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        // Pin HTTP/1.1 like the other admin downstream clients (Account/AccountTenant/Auth):
        // internal IAM service-to-service calls are cleartext HTTP/1.1, and pinning avoids the
        // JDK HttpClient's opportunistic h2c upgrade attempt against the (HTTP/1.1-only) target.
        // (ResilienceClientFactory.buildRestClient pins HTTP/1.1 for this identical reason.)
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.tokenProvider = tokenProvider;
    }

    /**
     * TASK-BE-656 — {@code tenantId} is the tenant {@code QueryTenantScopeGate} resolved for the audit query
     * ({@code "*"} = every tenant), sent as the required {@code tenantId} parameter (security-query-api.md
     * § login-history «테넌트»). Before the ticket nothing was sent and security-service read {@code fan-platform}.
     */
    @Retry(name = "securityService")
    @CircuitBreaker(name = "securityService")
    public List<LoginHistoryEntry> queryLoginHistory(String tenantId, String accountId, Instant from, Instant to) {
        LoginHistoryResponse resp = callGet(
                "/internal/security/login-history", tenantId, accountId, from, to, LoginHistoryResponse.class);
        return resp == null || resp.content() == null ? Collections.emptyList() : resp.content();
    }

    /** TASK-BE-656 — {@code tenantId} as on {@link #queryLoginHistory}. */
    @Retry(name = "securityService")
    @CircuitBreaker(name = "securityService")
    public List<SuspiciousEventEntry> querySuspiciousEvents(String tenantId, String accountId, Instant from, Instant to) {
        SuspiciousResponse resp = callGet(
                "/internal/security/suspicious-events", tenantId, accountId, from, to, SuspiciousResponse.class);
        return resp == null || resp.content() == null ? Collections.emptyList() : resp.content();
    }

    /**
     * TASK-MONO-811 — {@code GET /internal/security/metrics/daily} (security-query-api.md): per-KST-date
     * {@code loginSuccess} / {@code loginFailure} / {@code accountLocked} for {@code [from, to)}.
     * {@code tenantId} is the tenant {@code QueryTenantScopeGate} resolved ({@code "*"} = every tenant).
     * Fail-fast like the audit reads: any failure is a {@link DownstreamFailureException} (503) — never an
     * empty list, which the overview would show as «0».
     */
    @Retry(name = "securityService")
    @CircuitBreaker(name = "securityService")
    public SecurityMetricsDaily loginMetricsDaily(String tenantId, Instant from, Instant to) {
        String path = "/internal/security/metrics/daily";
        SecurityMetricsDaily resp;
        try {
            resp = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParam("tenantId", "{tenantId}")
                            .queryParam("from", "{from}")
                            .queryParam("to", "{to}")
                            .build(java.util.Map.of("tenantId", tenantId,
                                    "from", from.toString(), "to", to.toString())))
                    .headers(this::applyAuth)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, r) -> {
                        throw HttpClientErrorException.create(
                                r.getStatusCode(), r.getStatusText(),
                                r.getHeaders(), r.getBody().readAllBytes(), null);
                    })
                    .body(SecurityMetricsDaily.class);
        } catch (RestClientResponseException e) {
            log.warn("security-service returned {} on {}: {}", e.getStatusCode(), path, e.getMessage());
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException(
                        "security-service error " + e.getStatusCode().value(), e);
            }
            throw new DownstreamFailureException(
                    "security-service error " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("security-service call failed on {}", path, e);
            throw new DownstreamFailureException("security-service unavailable", e);
        }
        if (resp == null || resp.days() == null) {
            throw new DownstreamFailureException("security-service returned no metrics body");
        }
        return resp;
    }

    private <T> T callGet(String path, String tenantId, String accountId, Instant from, Instant to,
                          Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParam("tenantId", "{tenantId}")
                            .queryParamIfPresent("accountId", java.util.Optional.ofNullable(accountId))
                            .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                            .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                            .build(java.util.Map.of("tenantId", tenantId)))
                    .headers(this::applyAuth)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(responseType);
        } catch (RestClientResponseException e) {
            log.warn("security-service returned {} on {}: {}", e.getStatusCode(), path, e.getMessage());
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException(
                        "security-service error " + e.getStatusCode().value(), e);
            }
            throw new DownstreamFailureException(
                    "security-service error " + e.getStatusCode().value(), e);
        } catch (DownstreamFailureException e) {
            throw e;
        } catch (Exception e) {
            log.error("security-service call failed on {}", path, e);
            throw new DownstreamFailureException("security-service unavailable", e);
        }
    }

    // TASK-BE-318b: authenticate via GAP client_credentials Bearer JWT
    // (security /internal/** dual-allows JWT or X-Internal-Token, BE-317).
    private void applyAuth(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokenProvider.currentBearer());
    }

    public record LoginHistoryEntry(
            String eventId,
            String accountId,
            String outcome,
            String ipMasked,
            String geoCountry,
            Instant occurredAt
    ) {}

    /**
     * TASK-BE-656 — the contract's shape (security-query-api.md § suspicious-events: {@code id} · {@code ruleCode}
     * · {@code detectedAt}). Before the ticket this record read {@code eventId} · {@code signalType} ·
     * {@code ipMasked} · {@code occurredAt}, none of which security-service sends, so every suspicious row reached
     * the audit view with a null id, type and time. {@code evidence} is not carried into the audit view.
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SuspiciousEventEntry(
            String id,
            String accountId,
            String ruleCode,
            int riskScore,
            String actionTaken,
            Instant detectedAt
    ) {}

    public record LoginHistoryResponse(List<LoginHistoryEntry> content) {}

    public record SuspiciousResponse(List<SuspiciousEventEntry> content) {}

    /** TASK-MONO-811 — body of {@code GET /internal/security/metrics/daily}. Counts only. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SecurityMetricsDaily(String tenantId, String from, String to, List<Day> days) {
        /** {@code date} = {@code yyyy-MM-dd}, a KST date. */
        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        public record Day(String date, long loginSuccess, long loginFailure, long accountLocked) {}
    }
}
