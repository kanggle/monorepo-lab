package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantOrgNodeConflictException;
import com.example.admin.application.orgnode.TenantPlacementView;
import com.example.admin.application.port.TenantPlacementPort;
import com.example.common.resilience.ResilienceClientFactory;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07) — the {@link TenantPlacementPort} adapter onto
 * account-service's {@code /internal/tenant-placements/**}. Same circuit breaker, Bearer JWT and
 * failure mapping as {@link AccountServiceOrgNodeClient}; the same short read timeout, because
 * the placement read sits on the authorization path of the placement write.
 *
 * <p><b>No {@code @Retry}.</b> The write is conditional ({@code expectedOrgNodeId}) and
 * state-idempotent, so a retry would be safe — but a retry after a 409 or 404 would be wrong,
 * and the org-node sibling keeps every call single-shot for latency on the authorization path.
 */
@Slf4j
@Component
public class AccountServiceTenantPlacementClient implements TenantPlacementPort {

    private static final String CB_NAME = "accountService";
    private static final String BASE = "/internal/tenant-placements";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceTenantPlacementClient(
            @Value("${admin.account-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.org-node.read-timeout-ms:3000}") int readTimeoutMs,
            IamClientCredentialsTokenProvider tokenProvider) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.tokenProvider = tokenProvider;
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public String currentOrgNodeId(String tenantId) {
        PlacementResponse response = exchange(
                () -> restClient.get()
                        .uri(BASE + "/{tenantId}", tenantId)
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(PlacementResponse.class),
                "GET " + BASE + "/{tenantId}", tenantId, null);
        if (response == null) {
            // An empty 200 is not "ungrouped" — an unreadable placement must not be read as
            // the weakest source rule. Fail the request instead.
            throw new DownstreamFailureException("account-service returned an empty placement", null);
        }
        return response.orgNodeId();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public TenantPlacementView preview(String tenantId, String targetOrgNodeId) {
        EffectResponse response = exchange(
                () -> restClient.get()
                        .uri(uriBuilder -> {
                            var b = uriBuilder.path(BASE + "/{tenantId}/preview");
                            if (targetOrgNodeId != null) b = b.queryParam("orgNodeId", targetOrgNodeId);
                            return b.build(tenantId);
                        })
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(EffectResponse.class),
                "GET " + BASE + "/{tenantId}/preview", tenantId, targetOrgNodeId);
        return toView(response, tenantId);
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public TenantPlacementView place(String tenantId, String targetOrgNodeId, String expectedOrgNodeId) {
        // HashMap, not Map.of: both values are legitimately null and must be sent as JSON null.
        Map<String, Object> body = new HashMap<>();
        body.put("orgNodeId", targetOrgNodeId);
        body.put("expectedOrgNodeId", expectedOrgNodeId);
        EffectResponse response = exchange(
                () -> restClient.put()
                        .uri(BASE + "/{tenantId}", tenantId)
                        .headers(this::addInternalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(EffectResponse.class),
                "PUT " + BASE + "/{tenantId}", tenantId, targetOrgNodeId);
        return toView(response, tenantId);
    }

    // ---- exchange + error mapping ---------------------------------------------

    private <T> T exchange(java.util.function.Supplier<T> call, String what, String tenantId, String orgNodeId) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} ({}) on {}", status, code, what);
            if (status == 404) {
                // Two 404s on this surface; the authority's code says which. An unknown code is
                // read as the tenant one — the answer that discloses nothing about any node.
                if ("ORG_NODE_NOT_FOUND".equals(code)) {
                    throw new OrgNodeNotFoundException("Org node not found: " + orgNodeId);
                }
                throw new TenantNotFoundException(tenantId);
            }
            if (status == 409) {
                throw new TenantOrgNodeConflictException(
                        "Tenant '" + tenantId + "' placement changed before the write; nothing was written");
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException("account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            throw e;
        } catch (OrgNodeNotFoundException | TenantNotFoundException | TenantOrgNodeConflictException
                 | DownstreamFailureException e) {
            throw e;
        } catch (Exception e) {
            log.error("account-service {} failed", what, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    private static final RestClient.ResponseSpec.ErrorHandler RAISE = (req, resp) -> {
        throw HttpClientErrorException.create(
                resp.getStatusCode(), resp.getStatusText(),
                resp.getHeaders(), resp.getBody().readAllBytes(), null);
    };

    private void addInternalHeaders(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokenProvider.currentBearer());
    }

    private String extractErrorCode(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode() || root.isNull()) return null;
            JsonNode code = root.get("code");
            return code != null && code.isTextual() ? code.asText() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static TenantPlacementView toView(EffectResponse r, String tenantId) {
        if (r == null) {
            throw new DownstreamFailureException(
                    "account-service returned an empty placement effect for tenant " + tenantId, null);
        }
        return new TenantPlacementView(r.tenantId(), r.fromOrgNodeId(), r.toOrgNodeId(),
                nonNull(r.domainsBefore()), nonNull(r.domainsAfter()),
                nonNull(r.lostDomains()), nonNull(r.gainedDomains()),
                Boolean.TRUE.equals(r.changed()));
    }

    private static List<String> nonNull(List<String> l) {
        return l == null ? List.of() : l;
    }

    // ---- wire DTOs ------------------------------------------------------------

    private record PlacementResponse(String tenantId, String orgNodeId) {}

    /** Preview and write share the shape; {@code changed} is absent on a preview. */
    private record EffectResponse(
            String tenantId,
            String fromOrgNodeId,
            String toOrgNodeId,
            List<String> domainsBefore,
            List<String> domainsAfter,
            List<String> lostDomains,
            List<String> gainedDomains,
            Boolean changed
    ) {}
}
