package com.example.admin.infrastructure.client;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.exception.OrgNodeInvariantViolationException;
import com.example.admin.application.exception.OrgNodeNotFoundException;
import com.example.admin.application.orgnode.CeilingView;
import com.example.admin.application.orgnode.OrgNodeView;
import com.example.admin.application.port.OrgNodePort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
 * TASK-BE-492 (ADR-MONO-047 D6) — the {@link OrgNodePort} adapter onto account-service's
 * {@code /internal/org-nodes/**} authority (TASK-BE-491). Mirrors
 * {@link AccountServiceTenantClient}: same {@code accountService} circuit breaker, same
 * IAM {@code client_credentials} Bearer JWT, same downstream-failure mapping.
 *
 * <p><b>No {@code @Retry}, and a short read timeout.</b> Two of these calls
 * ({@code subtreeTenantIds}, {@code effectiveCeiling}) sit on the <em>permission-check</em>
 * path. Retrying them would multiply the latency an authorization decision waits for, and
 * the 10s default read timeout would let a hung account-service pin a request thread (and,
 * via {@code AdminGrantScopeEvaluator}'s read-only transaction, a DB connection) for the
 * whole window. The task's rule is explicit: <i>a slow account-service must time the
 * permission check out CLOSED, not open</i>. So this client fails fast and the fail-closed
 * resolvers turn that into "no reach", never "all reach".
 *
 * <p>422 from the authority is carried through verbatim
 * ({@link OrgNodeInvariantViolationException} keeps the code) — admin-service must not
 * re-implement the cycle / depth / ceiling-subset invariants, or the duplicate will drift
 * from the enforcing one.
 */
@Slf4j
@Component
public class AccountServiceOrgNodeClient implements OrgNodePort {

    private static final String CB_NAME = "accountService";
    private static final String BASE = "/internal/org-nodes";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceOrgNodeClient(
            @Value("${admin.account-service.base-url}") String baseUrl,
            @Value("${admin.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${admin.org-node.read-timeout-ms:3000}") int readTimeoutMs,
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
    @CircuitBreaker(name = CB_NAME)
    public List<OrgNodeView> list() {
        OrgNodeListResponse response = exchange(
                () -> restClient.get()
                        .uri(BASE)
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(OrgNodeListResponse.class),
                "GET " + BASE, null);
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(AccountServiceOrgNodeClient::toView).toList();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public OrgNodeView create(String name, String parentId, CeilingView ceiling) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("parentId", parentId);
        body.put("ceiling", toCeilingPayload(ceiling));
        return toView(exchange(
                () -> restClient.post()
                        .uri(BASE)
                        .headers(this::addInternalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(OrgNodeResponse.class),
                "POST " + BASE, null));
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public OrgNodeView get(String orgNodeId) {
        return toView(exchange(
                () -> restClient.get()
                        .uri(BASE + "/" + orgNodeId)
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(OrgNodeResponse.class),
                "GET " + BASE + "/{id}", orgNodeId));
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public OrgNodeView update(String orgNodeId, String name, String parentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (name != null) body.put("name", name);
        if (parentId != null) body.put("parentId", parentId);
        return toView(exchange(
                () -> restClient.patch()
                        .uri(BASE + "/" + orgNodeId)
                        .headers(this::addInternalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(OrgNodeResponse.class),
                "PATCH " + BASE + "/{id}", orgNodeId));
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public void delete(String orgNodeId) {
        exchange(
                () -> restClient.delete()
                        .uri(BASE + "/" + orgNodeId)
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .toBodilessEntity(),
                "DELETE " + BASE + "/{id}", orgNodeId);
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public OrgNodeView setCeiling(String orgNodeId, CeilingView ceiling) {
        return toView(exchange(
                () -> restClient.put()
                        .uri(BASE + "/" + orgNodeId + "/ceiling")
                        .headers(this::addInternalHeaders)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(toCeilingPayload(ceiling))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(OrgNodeResponse.class),
                "PUT " + BASE + "/{id}/ceiling", orgNodeId));
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public List<String> subtreeTenantIds(String orgNodeId) {
        SubtreeTenantsResponse response = exchange(
                () -> restClient.get()
                        .uri(BASE + "/" + orgNodeId + "/tenants")
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(SubtreeTenantsResponse.class),
                "GET " + BASE + "/{id}/tenants", orgNodeId);
        return response == null || response.tenantIds() == null ? List.of() : response.tenantIds();
    }

    @Override
    @CircuitBreaker(name = CB_NAME)
    public CeilingView effectiveCeiling(String orgNodeId) {
        CeilingPayload payload = exchange(
                () -> restClient.get()
                        .uri(BASE + "/" + orgNodeId + "/effective-ceiling")
                        .headers(this::addInternalHeaders)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, RAISE)
                        .body(CeilingPayload.class),
                "GET " + BASE + "/{id}/effective-ceiling", orgNodeId);
        return toCeilingView(payload);
    }

    // ---- Shared exchange + error mapping -------------------------------------

    /**
     * Runs {@code call} and maps every failure to the canonical exception family. 404 →
     * {@link OrgNodeNotFoundException}; 422 → {@link OrgNodeInvariantViolationException}
     * carrying the authority's code; other 4xx → {@link NonRetryableDownstreamException};
     * 5xx / IO / timeout → {@link DownstreamFailureException}.
     *
     * <p>{@code CallNotPermittedException} (circuit open) is a {@code RuntimeException} but
     * NOT a {@code RestClientResponseException}, and must NOT be swallowed into
     * {@code DownstreamFailureException} here — the advice maps it to its own 503
     * {@code CIRCUIT_OPEN}. It is therefore rethrown untouched.
     */
    private <T> T exchange(java.util.function.Supplier<T> call, String what, String orgNodeId) {
        try {
            return call.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String code = extractErrorCode(e.getResponseBodyAsByteArray());
            log.warn("account-service returned {} ({}) on {}", status, code, what);
            if (status == 404) {
                throw new OrgNodeNotFoundException("Org node not found: " + orgNodeId);
            }
            if (status == 422) {
                throw new OrgNodeInvariantViolationException(code, e.getMessage());
            }
            if (e.getStatusCode().is4xxClientError()) {
                throw new NonRetryableDownstreamException("account-service error " + status, e, status, code);
            }
            throw new DownstreamFailureException("account-service error " + status, e);
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            throw e;
        } catch (OrgNodeNotFoundException | OrgNodeInvariantViolationException
                 | DownstreamFailureException e) {
            // DownstreamFailureException covers NonRetryableDownstreamException (its subclass).
            throw e;
        } catch (Exception e) {
            log.error("account-service {} failed", what, e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    /**
     * Rethrows an error response as {@link HttpClientErrorException} so {@link #exchange}
     * sees a {@link RestClientResponseException} carrying the body (and therefore the
     * authority's error {@code code}). Byte-identical to the inline handler in
     * {@link AccountServiceTenantClient}.
     */
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

    // ---- Wire mapping ---------------------------------------------------------

    private static Map<String, Object> toCeilingPayload(CeilingView ceiling) {
        Map<String, Object> payload = new LinkedHashMap<>();
        CeilingView c = ceiling == null ? CeilingView.unbounded() : ceiling;
        payload.put("mode", c.mode());
        if (!c.isUnbounded()) {
            payload.put("domains", c.domains());
        }
        return payload;
    }

    /**
     * Absent / malformed ceiling → {@link CeilingView#failClosed()}. Never {@code UNBOUNDED}:
     * an unreadable ceiling must narrow, not widen.
     */
    private static CeilingView toCeilingView(CeilingPayload p) {
        if (p == null || p.mode() == null) {
            return CeilingView.failClosed();
        }
        if (CeilingView.MODE_UNBOUNDED.equals(p.mode())) {
            return CeilingView.unbounded();
        }
        if (CeilingView.MODE_BOUNDED.equals(p.mode())) {
            return CeilingView.bounded(p.domains() == null ? List.of() : p.domains());
        }
        log.error("Unknown ceiling mode '{}' from account-service — failing closed", p.mode());
        return CeilingView.failClosed();
    }

    private static OrgNodeView toView(OrgNodeResponse r) {
        if (r == null) return null;
        return new OrgNodeView(r.orgNodeId(), r.parentId(), r.name(), r.depth(),
                toCeilingView(r.ceiling()), r.createdAt(), r.updatedAt());
    }

    // ---- Response DTOs --------------------------------------------------------

    private record CeilingPayload(String mode, List<String> domains) {}

    private record OrgNodeResponse(
            String orgNodeId,
            String parentId,
            String name,
            int depth,
            CeilingPayload ceiling,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private record OrgNodeListResponse(List<OrgNodeResponse> items) {}

    private record SubtreeTenantsResponse(List<String> tenantIds) {}
}
