package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Calls account-service internal endpoints to lock/unlock accounts.
 * Contract: specs/contracts/http/internal/admin-to-account.md
 */
@Slf4j
@Component
public class AccountServiceClient {

    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceClient(
            @Value("${admin.account-service.base-url}") String baseUrl,
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

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public AccountSearchResponse search(String email) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/accounts")
                            .queryParam("email", email)
                            .build())
                    .headers(h -> {
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                    })
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                        throw org.springframework.web.client.HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(AccountSearchResponse.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("account-service returned {} on /internal/accounts: {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamFailureException("account-service search error", e);
        } catch (Exception e) {
            log.error("account-service search call failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public AccountSearchResponse listAll(int page, int size) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/accounts")
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .headers(h -> {
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                    })
                    .retrieve()
                    .onStatus(org.springframework.http.HttpStatusCode::isError, (req, resp) -> {
                        throw org.springframework.web.client.HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(AccountSearchResponse.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            log.warn("account-service returned {} on /internal/accounts (listAll): {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamFailureException("account-service listAll error", e);
        } catch (Exception e) {
            log.error("account-service listAll call failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public AccountDetailResponse getDetail(String accountId) {
        return callGet("/internal/accounts/" + accountId, null, AccountDetailResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public LockResponse lock(String accountId,
                             String operatorId,
                             String reason,
                             String ticketId,
                             String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "ADMIN_LOCK");
        body.put("operatorId", operatorId);
        if (ticketId != null) body.put("ticketId", ticketId);

        return callPost("/internal/accounts/" + accountId + "/lock",
                body, operatorId, idempotencyKey, LockResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public LockResponse unlock(String accountId,
                               String operatorId,
                               String reason,
                               String ticketId,
                               String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "ADMIN_UNLOCK");
        body.put("operatorId", operatorId);
        if (ticketId != null) body.put("ticketId", ticketId);

        return callPost("/internal/accounts/" + accountId + "/unlock",
                body, operatorId, idempotencyKey, LockResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public GdprDeleteResponse gdprDelete(String accountId,
                                          String operatorId,
                                          String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "REGULATED_DELETION");
        body.put("operatorId", operatorId);

        return callPost("/internal/accounts/" + accountId + "/gdpr-delete",
                body, operatorId, idempotencyKey, GdprDeleteResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public DataExportResponse export(String accountId, String operatorId) {
        return callGet("/internal/accounts/" + accountId + "/export",
                operatorId, DataExportResponse.class);
    }

    private <T> T callGet(String path, String operatorId, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(path)
                    .headers(h -> {
                        if (operatorId != null) h.add("X-Operator-ID", operatorId);
                        if (internalToken != null && !internalToken.isBlank()) {
                            h.add("X-Internal-Token", internalToken);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(responseType);
        } catch (RestClientResponseException e) {
            log.warn("account-service returned {} on {}: {}", e.getStatusCode(), path, e.getMessage());
            if (e.getStatusCode().is4xxClientError()) {
                String code = extractErrorCode(e.getResponseBodyAsByteArray());
                throw new NonRetryableDownstreamException(
                        "account-service error " + e.getStatusCode().value(), e,
                        e.getStatusCode().value(), code);
            }
            throw new DownstreamFailureException(
                    "account-service error " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("account-service call failed on {}", path, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    private <T> T callPost(String path, Map<String, Object> body,
                           String operatorId, String idempotencyKey, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(path)
                    .headers(h -> {
                        h.add("Idempotency-Key", idempotencyKey);
                        if (operatorId != null) h.add("X-Operator-ID", operatorId);
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
                    .body(responseType);
        } catch (RestClientResponseException e) {
            log.warn("account-service returned {} on {}: {}", e.getStatusCode(), path, e.getMessage());
            if (e.getStatusCode().is4xxClientError()) {
                String code = extractErrorCode(e.getResponseBodyAsByteArray());
                throw new NonRetryableDownstreamException(
                        "account-service error " + e.getStatusCode().value(), e,
                        e.getStatusCode().value(), code);
            }
            throw new DownstreamFailureException(
                    "account-service error " + e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("account-service call failed on {}", path, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    /**
     * Extract {@code code} (or nested {@code error.code}) from a downstream
     * error body. Returns {@code null} when the body is empty or unparseable —
     * callers must not depend on this being non-null.
     */
    private String extractErrorCode(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode() || root.isNull()) return null;
            JsonNode code = root.get("code");
            if (code != null && code.isTextual()) return code.asText();
            JsonNode error = root.get("error");
            if (error != null && error.isObject()) {
                JsonNode nested = error.get("code");
                if (nested != null && nested.isTextual()) return nested.asText();
            }
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    public record AccountDetailResponse(
            String id,
            String email,
            String status,
            Instant createdAt,
            AccountDetailProfile profile
    ) {}

    public record AccountDetailProfile(
            String displayName,
            String phoneMasked
    ) {}

    public record AccountSearchResponse(
            java.util.List<AccountSummaryItem> content,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {}

    public record AccountSummaryItem(
            String id,
            String email,
            String status,
            Instant createdAt
    ) {}

    public record LockResponse(
            String accountId,
            String previousStatus,
            String currentStatus,
            Instant lockedAt,
            Instant unlockedAt
    ) {}

    public record GdprDeleteResponse(
            String accountId,
            String status,
            String emailHash,
            Instant maskedAt
    ) {}

    public record DataExportResponse(
            String accountId,
            String email,
            String status,
            Instant createdAt,
            DataExportProfile profile,
            Instant exportedAt
    ) {}

    public record DataExportProfile(
            String displayName,
            String phoneNumber,
            String birthDate,
            String locale,
            String timezone
    ) {}
}
