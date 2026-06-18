package com.example.product.infrastructure.client;

import com.example.product.application.port.SellerAccountProvisioner;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account-service adapter for seller-operator account provisioning + deactivation
 * (ADR-MONO-042 D2/D4/D5). MIRRORS admin-service {@code AccountServiceClient}: a
 * {@link RestClient} over the account {@code /internal/**} endpoints, authenticated with
 * a GAP {@code client_credentials} Bearer JWT ({@link IamClientCredentialsTokenProvider},
 * ADR-005 단계 3b). product-service has NO resilience4j (unlike admin-service), so the
 * resilience is the explicit FAIL-SOFT try/catch the D3 stance requires — exactly the
 * behavior admin's {@code resolveOrCreateIdentity} swallow-to-null established.
 *
 * <p>Endpoints reused (verified present 2026-06-18, NOT modified by this task):
 * <ul>
 *   <li>{@code POST /internal/tenants/{t}/accounts} — mint the seller-operator account
 *       (role {@code SELLER}).</li>
 *   <li>{@code POST /internal/tenants/{t}/identities:resolveOrCreate} — born-unified
 *       identity (D5; {@code reuseExisting=true} converges same-email principals).</li>
 *   <li>{@code POST /internal/accounts/{accountId}/lock} — seller SUSPEND (D4).</li>
 *   <li>{@code PATCH /internal/tenants/{t}/accounts/{accountId}/status} — seller CLOSE
 *       (D4 deactivate).</li>
 * </ul>
 */
@Slf4j
@Component
public class AccountServiceSellerProvisioner implements SellerAccountProvisioner {

    /**
     * account-service status literal (AccountStatus) for the seller-CLOSE deactivation path
     * (D4). MUST be a valid {@code AccountStatus} enum constant or {@code AccountStatus.valueOf}
     * throws server-side (400) and the fail-soft swallow silently makes CLOSE a cosmetic no-op
     * — the exact D4-B alternative the ADR REJECTED.
     *
     * <p><b>Why {@code LOCKED}</b> (and not {@code DEACTIVATED}/{@code DORMANT}/{@code DELETED}):
     * <ul>
     *   <li>{@code DEACTIVATED} is NOT a member of account-service {@code AccountStatus}
     *       ({@code ACTIVE, LOCKED, DORMANT, DELETED}) — the original literal was invalid.</li>
     *   <li>The {@code /status} EP hardcodes reason {@code OPERATOR_PROVISIONING_STATUS_CHANGE};
     *       {@code AccountStatusMachine} permits that reason for {@code ACTIVE→LOCKED} (and the
     *       idempotent {@code LOCKED→LOCKED}) but NOT for {@code →DORMANT} (only {@code DORMANT_365D}
     *       reaches DORMANT), so DORMANT is unreachable here.</li>
     *   <li>{@code LOCKED} revokes the seller-operator's ability to authenticate (D4 intent:
     *       "deactivation is real, not just a label"), sets NO {@code deletedAt}, and emits only
     *       {@code account.status.changed}/{@code account.locked} — it does NOT fire
     *       {@code account.deleted}, so a seller CLOSE never triggers the ADR-037 PII-anonymization
     *       deletion cascade. (Only the separate {@code deleteAccount}/GDPR path emits that.)</li>
     *   <li>{@code DELETED} is rejected: it sets {@code deletedAt} and is the GDPR/deletion
     *       lifecycle state — semantically wrong + destructive for an operator seller CLOSE.</li>
     * </ul>
     */
    private static final String STATUS_DEACTIVATED = "LOCKED";

    private final RestClient restClient;
    private final IamClientCredentialsTokenProvider tokenProvider;
    private final String sellerRole;

    public AccountServiceSellerProvisioner(
            @Value("${iam.account-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${iam.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${iam.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            @Value("${iam.seller.role:SELLER}") String sellerRole,
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
        this.sellerRole = sellerRole;
    }

    @Override
    public ProvisioningResult provision(String tenantId, String sellerId, String displayName) {
        String email = sellerEmail(tenantId, sellerId);
        try {
            // [1] mint the seller-operator account (D2). Idempotent at account-service on
            //     (tenant, email); a re-onboard converges rather than duplicating.
            ProvisionAccountResponse acct = mintAccount(tenantId, email, displayName);
            if (acct == null || acct.accountId() == null) {
                log.warn("seller provisioning: account mint returned no accountId "
                        + "(fail-soft, seller stays PENDING) tenant={} seller={}", tenantId, sellerId);
                return ProvisioningResult.failed();
            }
            // [2] born-unified identity (D5). Best-effort: a null identity does not fail
            //     provisioning (the account is what makes the seller operable).
            String identityId = resolveOrCreateIdentity(tenantId, email);
            return ProvisioningResult.success(acct.accountId(), identityId);
        } catch (Exception e) {
            // FAIL-SOFT (D3): onboarding must NOT block on IAM infra. The seller stays
            // PENDING_PROVISIONING and is retryable.
            log.warn("seller provisioning failed (fail-soft, seller stays PENDING) "
                    + "tenant={} seller={}: {}", tenantId, sellerId, e.getMessage());
            return ProvisioningResult.failed();
        }
    }

    @Override
    public void lockAccount(String tenantId, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return; // net-zero: no backing account (legacy/PENDING seller)
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "ADMIN_LOCK");
        body.put("operatorId", "product-service");
        try {
            restClient.post()
                    .uri("/internal/accounts/{accountId}/lock", accountId)
                    .headers(h -> {
                        h.add("Idempotency-Key", UUID.randomUUID().toString());
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(resp.getStatusCode(),
                                resp.getStatusText(), resp.getHeaders(),
                                resp.getBody().readAllBytes(), null);
                    })
                    .toBodilessEntity();
        } catch (Exception e) {
            // Fail-soft: the seller's domain SUSPEND already applied; the lock is
            // retryable. (Re-locking an already-locked account is idempotent at the EP.)
            log.warn("seller account lock failed (fail-soft) tenant={} account={}: {}",
                    tenantId, accountId, e.getMessage());
        }
    }

    @Override
    public void deactivateAccount(String tenantId, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return; // net-zero
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", STATUS_DEACTIVATED);
        body.put("operatorId", "product-service");
        try {
            restClient.patch()
                    .uri("/internal/tenants/{tenantId}/accounts/{accountId}/status", tenantId, accountId)
                    .headers(h -> {
                        h.add("X-Tenant-Id", tenantId);
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        throw HttpClientErrorException.create(resp.getStatusCode(),
                                resp.getStatusText(), resp.getHeaders(),
                                resp.getBody().readAllBytes(), null);
                    })
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("seller account deactivation failed (fail-soft) tenant={} account={}: {}",
                    tenantId, accountId, e.getMessage());
        }
    }

    private ProvisionAccountResponse mintAccount(String tenantId, String email, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", generatePassword());
        body.put("displayName", truncateDisplayName(displayName));
        body.put("roles", List.of(sellerRole));
        body.put("operatorId", "product-service");
        return restClient.post()
                .uri("/internal/tenants/{tenantId}/accounts", tenantId)
                .headers(h -> {
                    h.add("X-Tenant-Id", tenantId);
                    h.setBearerAuth(tokenProvider.currentBearer());
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw HttpClientErrorException.create(resp.getStatusCode(),
                            resp.getStatusText(), resp.getHeaders(),
                            resp.getBody().readAllBytes(), null);
                })
                .body(ProvisionAccountResponse.class);
    }

    private String resolveOrCreateIdentity(String tenantId, String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("reuseExisting", true); // born-unified convergence (D5; same-origin issuance)
        try {
            ResolveOrCreateIdentityResponse resp = restClient.post()
                    .uri("/internal/tenants/{tenantId}/identities:resolveOrCreate", tenantId)
                    .headers(h -> {
                        h.add("X-Tenant-Id", tenantId);
                        h.setBearerAuth(tokenProvider.currentBearer());
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp2) -> {
                        throw HttpClientErrorException.create(resp2.getStatusCode(),
                                resp2.getStatusText(), resp2.getHeaders(),
                                resp2.getBody().readAllBytes(), null);
                    })
                    .body(ResolveOrCreateIdentityResponse.class);
            return resp == null ? null : resp.identityId();
        } catch (Exception e) {
            // Identity is best-effort (D5 fail-soft): a missing identity does not fail
            // provisioning — the account already makes the seller operable, and the
            // identity is filled on re-provision.
            log.warn("seller identity resolveOrCreate failed (best-effort) tenant={}: {}",
                    tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * Truncate the seller display name to the account-service {@code ProvisionAccountRequest}
     * {@code @Size(max=100)} bound (m4). product-service {@code sellers.display_name} is
     * VARCHAR(255); a 101–255-char name would 400 the mint and strand the seller in
     * {@code PENDING_PROVISIONING}. Truncating client-side keeps the mint succeeding (the
     * seller-operator display name is cosmetic — it never authenticates by name).
     */
    private static final int MAX_ACCOUNT_DISPLAY_NAME = 100;

    private static String truncateDisplayName(String displayName) {
        if (displayName == null || displayName.length() <= MAX_ACCOUNT_DISPLAY_NAME) {
            return displayName;
        }
        return displayName.substring(0, MAX_ACCOUNT_DISPLAY_NAME);
    }

    /**
     * Deterministic seller-operator email keyed on (tenant, seller) so a re-onboard
     * converges on the SAME account/identity at account-service (idempotency, D5).
     */
    private static String sellerEmail(String tenantId, String sellerId) {
        return "seller+" + tenantId + "+" + sellerId + "@marketplace.local";
    }

    /**
     * The seller-operator account is provisioned, not self-service signed-up; it never
     * authenticates by password (it operates via the assume-tenant seller-scope claim,
     * D6). A random strong password satisfies the provisioning EP's min-length validation.
     */
    private static String generatePassword() {
        return "Sx" + UUID.randomUUID().toString().replace("-", "");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProvisionAccountResponse(String accountId, String tenantId, String email,
                                    String status, List<String> roles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResolveOrCreateIdentityResponse(String identityId, String outcome) {}
}
