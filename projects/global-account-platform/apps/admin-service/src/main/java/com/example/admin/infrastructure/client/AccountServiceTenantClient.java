package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-250: Calls account-service internal tenant endpoints.
 * Implements {@link TenantProvisioningPort}.
 *
 * <p>Uses the same {@code accountService} CB/retry config as {@link AccountServiceClient}.
 * account-service 409 → {@link TenantAlreadyExistsException},
 * 404 → {@link TenantNotFoundException},
 * 5xx/CB-open → {@link DownstreamFailureException} / CallNotPermittedException.
 */
@Slf4j
@Component
public class AccountServiceTenantClient implements TenantProvisioningPort {

    private static final String CB_NAME = "accountService";

    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceTenantClient(
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

    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public TenantSummary create(String tenantId, String displayName, String tenantType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("displayName", displayName);
        body.put("tenantType", tenantType);

        try {
            TenantResponse response = restClient.post()
                    .uri("/internal/tenants")
                    .headers(h -> addInternalHeaders(h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(TenantResponse.class);
            return toSummary(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} on POST /internal/tenants: {}", status, e.getMessage());
            if (status == 409) {
                throw new TenantAlreadyExistsException(tenantId);
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException(
                        "account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (Exception e) {
            log.error("account-service POST /internal/tenants failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public TenantSummary update(String tenantId, String displayName, String status) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (displayName != null) body.put("displayName", displayName);
        if (status != null) body.put("status", status);

        try {
            TenantResponse response = restClient.patch()
                    .uri("/internal/tenants/" + tenantId)
                    .headers(h -> addInternalHeaders(h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(TenantResponse.class);
            return toSummary(response);
        } catch (RestClientResponseException e) {
            int status2 = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} on PATCH /internal/tenants/{}: {}", status2, tenantId, e.getMessage());
            if (status2 == 404) {
                throw new TenantNotFoundException(tenantId);
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException(
                        "account-service error " + status2, e, status2, code);
            }
            throw new DownstreamFailureException("account-service error " + status2, e);
        } catch (Exception e) {
            log.error("account-service PATCH /internal/tenants/{} failed", tenantId, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public TenantSummary get(String tenantId) {
        try {
            TenantResponse response = restClient.get()
                    .uri("/internal/tenants/" + tenantId)
                    .headers(h -> addInternalHeaders(h))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(TenantResponse.class);
            return toSummary(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} on GET /internal/tenants/{}: {}", status, tenantId, e.getMessage());
            if (status == 404) {
                throw new TenantNotFoundException(tenantId);
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException(
                        "account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (Exception e) {
            log.error("account-service GET /internal/tenants/{} failed", tenantId, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public TenantPageSummary list(String statusFilter, String tenantTypeFilter, int page, int size) {
        try {
            TenantPageResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        var b = uriBuilder.path("/internal/tenants")
                                .queryParam("page", page)
                                .queryParam("size", size);
                        if (statusFilter != null) b = b.queryParam("status", statusFilter);
                        if (tenantTypeFilter != null) b = b.queryParam("tenantType", tenantTypeFilter);
                        return b.build();
                    })
                    .headers(h -> addInternalHeaders(h))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(TenantPageResponse.class);
            return toPageSummary(response);
        } catch (RestClientResponseException e) {
            log.warn("account-service returned {} on GET /internal/tenants: {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamFailureException("account-service list error", e);
        } catch (Exception e) {
            log.error("account-service GET /internal/tenants failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    private void addInternalHeaders(org.springframework.http.HttpHeaders h) {
        if (internalToken != null && !internalToken.isBlank()) {
            h.add("X-Internal-Token", internalToken);
        }
    }

    private String extractErrorCode(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode() || root.isNull()) return null;
            JsonNode code = root.get("code");
            if (code != null && code.isTextual()) return code.asText();
            return null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static TenantSummary toSummary(TenantResponse r) {
        if (r == null) return null;
        return new TenantSummary(
                r.tenantId(), r.displayName(), r.tenantType(), r.status(),
                r.createdAt(), r.updatedAt());
    }

    private static TenantPageSummary toPageSummary(TenantPageResponse r) {
        if (r == null) return new TenantPageSummary(List.of(), 0, 20, 0, 0);
        List<TenantSummary> items = r.items() == null ? List.of()
                : r.items().stream().map(AccountServiceTenantClient::toSummary).toList();
        return new TenantPageSummary(items, r.page(), r.size(), r.totalElements(), r.totalPages());
    }

    // ---- Response DTOs -------------------------------------------------------

    private record TenantResponse(
            String tenantId,
            String displayName,
            String tenantType,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private record TenantPageResponse(
            List<TenantResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
