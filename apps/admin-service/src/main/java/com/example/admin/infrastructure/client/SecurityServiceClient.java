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
    private final String internalToken;

    public SecurityServiceClient(
            @Value("${admin.security-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${admin.downstream.internal-token:}") String internalToken) {
        HttpClient httpClient = HttpClient.newBuilder()
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

    @Retry(name = "securityService")
    @CircuitBreaker(name = "securityService")
    public List<LoginHistoryEntry> queryLoginHistory(String accountId, Instant from, Instant to) {
        LoginHistoryResponse resp = callGet(
                "/internal/security/login-history", accountId, from, to, LoginHistoryResponse.class);
        return resp == null || resp.content() == null ? Collections.emptyList() : resp.content();
    }

    @Retry(name = "securityService")
    @CircuitBreaker(name = "securityService")
    public List<SuspiciousEventEntry> querySuspiciousEvents(String accountId, Instant from, Instant to) {
        SuspiciousResponse resp = callGet(
                "/internal/security/suspicious-events", accountId, from, to, SuspiciousResponse.class);
        return resp == null || resp.content() == null ? Collections.emptyList() : resp.content();
    }

    private <T> T callGet(String path, String accountId, Instant from, Instant to, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path)
                            .queryParamIfPresent("accountId", java.util.Optional.ofNullable(accountId))
                            .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                            .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                            .build())
                    .headers(this::applyInternalToken)
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

    private void applyInternalToken(org.springframework.http.HttpHeaders h) {
        if (internalToken != null && !internalToken.isBlank()) {
            h.add("X-Internal-Token", internalToken);
        }
    }

    public record LoginHistoryEntry(
            String eventId,
            String accountId,
            String outcome,
            String ipMasked,
            String geoCountry,
            Instant occurredAt
    ) {}

    public record SuspiciousEventEntry(
            String eventId,
            String accountId,
            String signalType,
            String ipMasked,
            Instant occurredAt
    ) {}

    public record LoginHistoryResponse(List<LoginHistoryEntry> content) {}

    public record SuspiciousResponse(List<SuspiciousEventEntry> content) {}
}
