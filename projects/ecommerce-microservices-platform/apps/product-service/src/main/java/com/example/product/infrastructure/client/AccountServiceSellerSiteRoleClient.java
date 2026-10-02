package com.example.product.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.product.application.port.SellerSiteRoleGateway;
import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.exception.SellerMemberAccountNotEligibleException;
import com.example.product.domain.exception.SellerRoleServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Account-service adapter for the store {@code SELLER} site role of seller members (ADR-MONO-079 D5,
 * TASK-MONO-752; {@code product-to-account.md} § 5 · § 6 → iam {@code consumer-site-roles.md}).
 *
 * <p>Same transport and auth as {@link AccountServiceSellerProvisioner} — a {@link RestClient} built by
 * {@link ResilienceClientFactory} over the same {@code iam.account-service.base-url}, the bearer minted FOR the
 * tenant by {@link TenantScopedIamTokenProvider} (ADR-MONO-076), {@code X-Tenant-Id} as defense-in-depth, and a
 * tenant-named path ({@code /internal/tenants/{tenantId}/**}, the only internal route of the IAM gateway —
 * TASK-MONO-737). No new client registration: the tenant this call names is the store tenant product-service
 * already exchanges for.
 *
 * <p>🔴 {@link #grant} is <b>fail-closed</b> (it throws — nobody becomes a member without IAM's yes);
 * {@link #revoke} is <b>fail-soft</b> ({@code false} — the caller keeps the member retryable).
 */
@Slf4j
@Component
public class AccountServiceSellerSiteRoleClient implements SellerSiteRoleGateway {

    static final String ROLE_SELLER = "SELLER";
    private static final String OPERATOR_ID = "product-service";
    private static final Set<String> NOT_ELIGIBLE_CODES =
            Set.of("SITE_ROLE_REQUIRES_POOL_ACCOUNT", "SITE_MEMBERSHIP_REQUIRED", "ACCOUNT_NOT_FOUND");

    private final RestClient restClient;
    private final TenantScopedIamTokenProvider tenantTokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AccountServiceSellerSiteRoleClient(
            @Value("${iam.account-service.base-url:http://localhost:8081}") String baseUrl,
            @Value("${iam.downstream.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${iam.downstream.read-timeout-ms:10000}") int readTimeoutMs,
            TenantScopedIamTokenProvider tenantTokenProvider) {
        this.restClient = ResilienceClientFactory.buildRestClient(baseUrl, connectTimeoutMs, readTimeoutMs);
        this.tenantTokenProvider = tenantTokenProvider;
    }

    @Override
    public void grant(String siteTenantId, String accountId, String invitedEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleName", ROLE_SELLER);
        body.put("expectedEmail", invitedEmail);
        body.put("operatorId", OPERATOR_ID);
        try {
            call("grant", siteTenantId, accountId, body);
        } catch (RestClientResponseException e) {
            String code = errorCode(e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 403 && "SITE_ROLE_EMAIL_MISMATCH".equals(code)) {
                throw new SellerInvitationEmailMismatchException();
            }
            if (code != null && NOT_ELIGIBLE_CODES.contains(code)) {
                throw new SellerMemberAccountNotEligibleException();
            }
            log.warn("seller site-role grant refused unexpectedly tenant={} account={} status={} code={}",
                    siteTenantId, accountId, e.getStatusCode().value(), code);
            throw new SellerRoleServiceUnavailableException();
        } catch (RuntimeException e) {
            log.warn("seller site-role grant failed (fail-closed) tenant={} account={}: {}",
                    siteTenantId, accountId, e.getMessage());
            throw new SellerRoleServiceUnavailableException();
        }
    }

    @Override
    public boolean revoke(String siteTenantId, String accountId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roleName", ROLE_SELLER);
        body.put("operatorId", OPERATOR_ID);
        try {
            call("revoke", siteTenantId, accountId, body);
            return true;
        } catch (Exception e) {
            log.warn("seller site-role revoke failed (fail-soft, member stays ACTIVE for retry) tenant={} account={}: {}",
                    siteTenantId, accountId, e.getMessage());
            return false;
        }
    }

    private void call(String verb, String tenantId, String accountId, Map<String, Object> body) {
        restClient.patch()
                .uri("/internal/tenants/{tenantId}/accounts/{accountId}/site-roles:" + verb, tenantId, accountId)
                .headers(h -> {
                    h.add("X-Tenant-Id", tenantId);
                    h.setBearerAuth(tenantTokenProvider.bearerFor(tenantId));
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw HttpClientErrorException.create(resp.getStatusCode(), resp.getStatusText(),
                            resp.getHeaders(), resp.getBody().readAllBytes(), null);
                })
                .toBodilessEntity();
    }

    private String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body).path("code");
            return node.isTextual() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
