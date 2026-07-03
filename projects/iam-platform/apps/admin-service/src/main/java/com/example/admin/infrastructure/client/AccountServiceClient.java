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
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceClient(
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

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public AccountSearchResponse search(String tenantId, String email) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/accounts")
                            .queryParam("email", email)
                            .queryParam("tenantId", tenantId)  // TASK-BE-357: tenant-scoped (was fan-platform hard-coded)
                            .build())
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(AccountSearchResponse.class);
        } catch (RestClientResponseException e) {
            log.warn("account-service returned {} on /internal/accounts: {}", e.getStatusCode(), e.getMessage());
            throw new DownstreamFailureException("account-service search error", e);
        } catch (Exception e) {
            log.error("account-service search call failed", e);
            throw new DownstreamFailureException("account-service unavailable", e);
        }
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public AccountSearchResponse listAll(String tenantId, int page, int size) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/accounts")
                            .queryParam("tenantId", tenantId)  // TASK-BE-357: tenant-scoped (was unscoped all-tenant scan)
                            .queryParam("page", page)
                            .queryParam("size", size)
                            .build())
                    .headers(h -> h.setBearerAuth(tokenProvider.currentBearer()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(
                                resp.getStatusCode(), resp.getStatusText(),
                                resp.getHeaders(), resp.getBody().readAllBytes(), null);
                    })
                    .body(AccountSearchResponse.class);
        } catch (RestClientResponseException e) {
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
        return callGet("/internal/accounts/" + accountId, null, null, AccountDetailResponse.class);
    }

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 (step 3c) — resolve a consumer account's central
     * {@code identity_id} via the step-3b internal EP
     * {@code GET /internal/tenants/{tenantId}/accounts/{accountId}/identity}
     * (TASK-BE-372). The EP is enumeration-safe: a foreign/missing account, or an
     * account with no identity yet, returns 200 with {@code identityId = null}.
     *
     * <p><b>Fail-CLOSED for the link decision.</b> Unlike the issuance fail-soft, the
     * operator-link is an authorization decision at link time, so any downstream
     * failure (account-service unavailable / errors) propagates as
     * {@link DownstreamFailureException} (or {@link NonRetryableDownstreamException}
     * for 4xx) — the use case treats that as a HARD FAILURE and does NOT link
     * (§ 1.3 no-silent-merge). A successful 200 with {@code identityId == null} is
     * also a link-fail (no resolvable identity) — but that is the use case's
     * decision, not an exception here.
     *
     * @return the resolved {@code identityId}, or {@code null} when the account has
     *         no central identity (200 + null).
     */
    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public String resolveIdentity(String tenantId, String accountId) {
        AccountIdentityResponse resp = callGetWithTenant(
                "/internal/tenants/" + tenantId + "/accounts/" + accountId + "/identity",
                tenantId, AccountIdentityResponse.class);
        return resp == null ? null : resp.identityId();
    }

    /**
     * TASK-BE-374 / ADR-MONO-034 U4 (step 3d) — resolve-or-create the central
     * {@code identity_id} for a (tenant, email) via the account-service EP
     * {@code POST /internal/tenants/{tenantId}/identities:resolveOrCreate}
     * (TASK-BE-374). Used by unified new-operator provisioning so every operator
     * born after step 3 is linked to a central identity.
     *
     * <p><b>FAIL-SOFT for provisioning</b> — the OPPOSITE of the 3c link's
     * fail-closed. Operator creation must NOT hard-fail on identity-infra
     * unavailability: any {@link DownstreamFailureException} /
     * {@link NonRetryableDownstreamException} (account-service down / errors) is
     * swallowed → returns {@code null} + {@code log.warn}. The operator is created
     * UNLINKED and can be linked later via the explicit step-3c surface.
     *
     * <p>The {@code reuseExisting} flag carries the no-silent-merge opt-in (U3): a
     * {@code 200 EXISTS_NOT_REUSED} (identity exists but caller did not opt in) also
     * yields {@code null} — the use case treats null identically (no link).
     *
     * @return the resolved/created {@code identityId}, or {@code null} when no
     *         identity was created/reused OR the downstream call failed (fail-soft).
     */
    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public String resolveOrCreateIdentity(String tenantId, String email, boolean reuseExisting) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("reuseExisting", reuseExisting);
        try {
            ResolveOrCreateIdentityResponse resp = callPostWithTenant(
                    "/internal/tenants/" + tenantId + "/identities:resolveOrCreate",
                    body, tenantId, ResolveOrCreateIdentityResponse.class);
            return resp == null ? null : resp.identityId();
        } catch (DownstreamFailureException e) {
            // FAIL-SOFT: provisioning must not block on identity-infra unavailability.
            // NonRetryableDownstreamException (4xx) is a subclass, so this single catch
            // covers both the retryable (5xx/timeout) and non-retryable (4xx) signals.
            log.warn("resolve-or-create identity failed (fail-soft, operator left unlinked) "
                    + "tenant={}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * TASK-BE-467 — {@code tenantId} is the actor's resolved active tenant, stamped
     * as {@code X-Tenant-Id} so account-service confines the target (cross-tenant →
     * 404 ACCOUNT_NOT_FOUND). {@code "*"} / null → account-service FAN default (net-zero).
     */
    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public LockResponse lock(String accountId,
                             String operatorId,
                             String reason,
                             String ticketId,
                             String idempotencyKey,
                             String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "ADMIN_LOCK");
        body.put("operatorId", operatorId);
        if (ticketId != null) body.put("ticketId", ticketId);

        return callPost("/internal/accounts/" + accountId + "/lock",
                body, operatorId, tenantId, idempotencyKey, LockResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public LockResponse unlock(String accountId,
                               String operatorId,
                               String reason,
                               String ticketId,
                               String idempotencyKey,
                               String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "ADMIN_UNLOCK");
        body.put("operatorId", operatorId);
        if (ticketId != null) body.put("ticketId", ticketId);

        return callPost("/internal/accounts/" + accountId + "/unlock",
                body, operatorId, tenantId, idempotencyKey, LockResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public GdprDeleteResponse gdprDelete(String accountId,
                                          String operatorId,
                                          String idempotencyKey,
                                          String tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "REGULATED_DELETION");
        body.put("operatorId", operatorId);

        return callPost("/internal/accounts/" + accountId + "/gdpr-delete",
                body, operatorId, tenantId, idempotencyKey, GdprDeleteResponse.class);
    }

    @Retry(name = "accountService")
    @CircuitBreaker(name = "accountService")
    public DataExportResponse export(String accountId, String operatorId, String tenantId) {
        return callGet("/internal/accounts/" + accountId + "/export",
                operatorId, tenantId, DataExportResponse.class);
    }

    /**
     * Shared {@code onStatus} handler: re-raises any error status from account-service
     * as an {@link HttpClientErrorException} carrying the response body, which
     * {@link #execute} then maps to the retryable/non-retryable downstream exceptions.
     */
    private static final RestClient.ResponseSpec.ErrorHandler ERROR_RAISER = (req, resp) -> {
        throw HttpClientErrorException.create(
                resp.getStatusCode(), resp.getStatusText(),
                resp.getHeaders(), resp.getBody().readAllBytes(), null);
    };

    /**
     * Runs a downstream account-service call and maps failures uniformly:
     * 4xx → {@link NonRetryableDownstreamException} (with the parsed error code),
     * any other failure → {@link DownstreamFailureException}. This try/catch was
     * previously duplicated verbatim across every callGet/callPost helper.
     */
    private <T> T execute(String path, java.util.function.Supplier<T> call) {
        try {
            return call.get();
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

    private <T> T callGet(String path, String operatorId, String tenantId, Class<T> responseType) {
        return execute(path, () -> restClient.get()
                .uri(path)
                .headers(h -> {
                    if (operatorId != null) h.add("X-Operator-ID", operatorId);
                    // TASK-BE-467: stamp the actor's active tenant so account-service
                    // confines the mutation target (cross-tenant → 404).
                    if (tenantId != null) h.add("X-Tenant-Id", tenantId);
                    // TASK-BE-318b: authenticate via GAP client_credentials Bearer JWT
                    // (account /internal/** dual-allows JWT or X-Internal-Token, BE-317).
                    h.setBearerAuth(tokenProvider.currentBearer());
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                .body(responseType));
    }

    /**
     * GET variant that stamps the {@code X-Tenant-Id} header (defense-in-depth tenant
     * scope re-check at the receiver, mirroring {@code AccountIdentityController}). The
     * client_credentials Bearer JWT is still attached. Error semantics identical to
     * {@link #callGet}: 4xx → {@link NonRetryableDownstreamException}, other failures →
     * {@link DownstreamFailureException} (the fail-closed signal for the link use case).
     */
    private <T> T callGetWithTenant(String path, String tenantId, Class<T> responseType) {
        return execute(path, () -> restClient.get()
                .uri(path)
                .headers(h -> {
                    if (tenantId != null) h.add("X-Tenant-Id", tenantId);
                    h.setBearerAuth(tokenProvider.currentBearer());
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                .body(responseType));
    }

    private <T> T callPost(String path, Map<String, Object> body,
                           String operatorId, String tenantId, String idempotencyKey, Class<T> responseType) {
        return execute(path, () -> restClient.post()
                .uri(path)
                .headers(h -> {
                    h.add("Idempotency-Key", idempotencyKey);
                    if (operatorId != null) h.add("X-Operator-ID", operatorId);
                    // TASK-BE-467: stamp the actor's active tenant so account-service
                    // confines the mutation target (cross-tenant → 404).
                    if (tenantId != null) h.add("X-Tenant-Id", tenantId);
                    // TASK-BE-318b: GAP client_credentials Bearer JWT (replaces X-Internal-Token).
                    h.setBearerAuth(tokenProvider.currentBearer());
                    h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                .body(responseType));
    }

    /**
     * POST variant that stamps {@code X-Tenant-Id} (defense-in-depth tenant-scope
     * re-check at the receiver, mirroring {@link #callGetWithTenant}) plus the
     * client_credentials Bearer JWT. No {@code Idempotency-Key}/{@code X-Operator-ID}
     * (the resolve-or-create EP is an idempotent provisioning primitive keyed by
     * {@code uk_identities_tenant_email}). Error semantics identical to
     * {@link #callPost}: 4xx → {@link NonRetryableDownstreamException}, other failures
     * → {@link DownstreamFailureException} — the CALLER decides fail-soft vs
     * fail-closed.
     */
    private <T> T callPostWithTenant(String path, Map<String, Object> body,
                                     String tenantId, Class<T> responseType) {
        return execute(path, () -> restClient.post()
                .uri(path)
                .headers(h -> {
                    if (tenantId != null) h.add("X-Tenant-Id", tenantId);
                    h.setBearerAuth(tokenProvider.currentBearer());
                    h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, ERROR_RAISER)
                .body(responseType));
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

    /**
     * TASK-BE-373 / ADR-MONO-034 U3 — response shape of the step-3b identity-resolve
     * EP ({@code GET /internal/tenants/{tenantId}/accounts/{accountId}/identity},
     * TASK-BE-372). {@code identityId} is {@code null} when the account has no central
     * identity (or does not exist in the tenant — enumeration-safe 200).
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountIdentityResponse(
            String accountId,
            String tenantId,
            String identityId
    ) {}

    /**
     * TASK-BE-374 / ADR-MONO-034 U4 — response shape of the step-3d resolve-or-create
     * EP ({@code POST /internal/tenants/{tenantId}/identities:resolveOrCreate}).
     * {@code identityId} is {@code null} when {@code outcome=EXISTS_NOT_REUSED} (an
     * identity exists but the caller did not opt in — no link, no merge per U3).
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ResolveOrCreateIdentityResponse(
            String identityId,
            String outcome
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
