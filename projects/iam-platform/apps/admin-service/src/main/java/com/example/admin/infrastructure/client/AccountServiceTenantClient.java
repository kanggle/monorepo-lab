package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.SubscriptionAlreadyExistsException;
import com.example.admin.application.exception.SubscriptionNotFoundException;
import com.example.admin.application.exception.SubscriptionTransitionInvalidException;
import com.example.admin.application.exception.TenantAlreadyExistsException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import com.example.admin.application.tenant.TenantDomainSubscriptionSummary;
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
public class AccountServiceTenantClient implements TenantProvisioningPort, TenantDomainSubscriptionPort {

    private static final String CB_NAME = "accountService";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceTenantClient(
            @Value("${admin.account-service.base-url}") String baseUrl,
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

    // TASK-BE-322 (ADR-MONO-019 D4): read ACTIVE tenant↔domain subscriptions from
    // the account-service entitlement authority (D2). Mirrors list() — same CB/retry,
    // same Bearer JWT auth, same downstream-failure mapping (5xx/CB-open → 503).
    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public List<TenantDomainSubscriptionSummary> listActiveSubscriptions() {
        try {
            SubscriptionListResponse response = restClient.get()
                    .uri("/internal/tenant-domain-subscriptions")
                    .headers(h -> addInternalHeaders(h))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(SubscriptionListResponse.class);
            return toSubscriptionSummaries(response);
        } catch (RestClientResponseException e) {
            log.warn("account-service returned {} on GET /internal/tenant-domain-subscriptions: {}",
                    e.getStatusCode(), e.getMessage());
            throw new DownstreamFailureException("account-service subscription list error", e);
        } catch (Exception e) {
            log.error("account-service GET /internal/tenant-domain-subscriptions failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    // TASK-BE-343 (ADR-MONO-023 D3): subscribe (create) — delegate the entitlement
    // write to account-service. account-service 404 TENANT_NOT_FOUND → TenantNotFoundException,
    // 409 SUBSCRIPTION_ALREADY_EXISTS → SubscriptionAlreadyExistsException,
    // 5xx/CB-open → DownstreamFailureException.
    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public SubscriptionMutationSummary subscribe(String tenantId, String domainKey,
                                                 String reason, String actorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("domainKey", domainKey);
        body.put("actorType", "operator");
        if (actorId != null) body.put("actorId", actorId);
        if (reason != null) body.put("reason", reason);
        try {
            SubscriptionMutationResponse response = restClient.post()
                    .uri("/internal/tenant-domain-subscriptions")
                    .headers(h -> addInternalHeaders(h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(SubscriptionMutationResponse.class);
            return toMutationSummary(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} ({}) on POST /internal/tenant-domain-subscriptions: {}",
                    status, code, e.getMessage());
            if (status == 404) {
                throw new TenantNotFoundException(tenantId);
            }
            if (status == 409) {
                throw new SubscriptionAlreadyExistsException(
                        "Subscription already exists: (" + tenantId + ", " + domainKey + ")");
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException("account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (Exception e) {
            log.error("account-service POST /internal/tenant-domain-subscriptions failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    // TASK-BE-343 (ADR-MONO-023 D1/D3): transition (suspend/resume/cancel).
    // account-service 404 SUBSCRIPTION_NOT_FOUND → SubscriptionNotFoundException,
    // 409 SUBSCRIPTION_TRANSITION_INVALID → SubscriptionTransitionInvalidException,
    // 5xx/CB-open → DownstreamFailureException.
    @Override
    @Retry(name = CB_NAME)
    @CircuitBreaker(name = CB_NAME)
    public SubscriptionMutationSummary changeStatus(String tenantId, String domainKey,
                                                    String targetStatus, String reason, String actorId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", targetStatus);
        body.put("actorType", "operator");
        if (actorId != null) body.put("actorId", actorId);
        if (reason != null) body.put("reason", reason);
        try {
            SubscriptionMutationResponse response = restClient.patch()
                    .uri("/internal/tenant-domain-subscriptions/" + tenantId + "/" + domainKey)
                    .headers(h -> addInternalHeaders(h))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(SubscriptionMutationResponse.class);
            return toMutationSummary(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} ({}) on PATCH /internal/tenant-domain-subscriptions/{}/{}: {}",
                    status, code, tenantId, domainKey, e.getMessage());
            if (status == 404) {
                throw new SubscriptionNotFoundException(
                        "Subscription not found: (" + tenantId + ", " + domainKey + ")");
            }
            if (status == 409) {
                throw new SubscriptionTransitionInvalidException(
                        "Illegal subscription transition for (" + tenantId + ", " + domainKey + ")");
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException("account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (Exception e) {
            log.error("account-service PATCH /internal/tenant-domain-subscriptions/{}/{} failed",
                    tenantId, domainKey, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    private static SubscriptionMutationSummary toMutationSummary(SubscriptionMutationResponse r) {
        if (r == null) return null;
        return new SubscriptionMutationSummary(
                r.tenantId(), r.domainKey(), r.previousStatus(), r.currentStatus(), r.occurredAt());
    }

    // TASK-BE-318b: authenticate via GAP client_credentials Bearer JWT
    // (account /internal/** dual-allows JWT or X-Internal-Token, BE-317).
    private void addInternalHeaders(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokenProvider.currentBearer());
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

    private static List<TenantDomainSubscriptionSummary> toSubscriptionSummaries(SubscriptionListResponse r) {
        if (r == null || r.items() == null) return List.of();
        return r.items().stream()
                .map(i -> new TenantDomainSubscriptionSummary(i.tenantId(), i.domainKey()))
                .toList();
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

    // TASK-BE-322: account /internal/tenant-domain-subscriptions response shape.
    private record SubscriptionListResponse(
            List<SubscriptionItemResponse> items
    ) {}

    private record SubscriptionItemResponse(
            String tenantId,
            String domainKey
    ) {}

    // TASK-BE-343: account POST/PATCH /internal/tenant-domain-subscriptions response shape.
    private record SubscriptionMutationResponse(
            String tenantId,
            String domainKey,
            String previousStatus,
            String currentStatus,
            Instant occurredAt
    ) {}
}
