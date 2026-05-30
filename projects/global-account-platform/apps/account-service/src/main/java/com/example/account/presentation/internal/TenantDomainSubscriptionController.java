package com.example.account.presentation.internal;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.application.service.TenantDomainSubscriptionQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): internal read surface exposing ACTIVE
 * tenant↔domain subscriptions for the admin-service console catalog projection
 * (ADR-019 D4).
 *
 * <p>URL prefix: {@code /internal/tenant-domain-subscriptions}
 * <p>Authentication: under the {@code /internal/} gate — GAP
 * {@code client_credentials} Bearer JWT (TASK-BE-319b receiver, fail-closed;
 * the test/standalone bypass profile populates an authenticated principal).
 * No extra security wiring is needed: the {@code /internal/} prefix is already
 * covered by {@code InternalApiFilter} + the OAuth2 resource-server
 * {@code .authenticated()} gate.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenant-domain-subscriptions")
public class TenantDomainSubscriptionController {

    private final TenantDomainSubscriptionQueryUseCase queryUseCase;

    /**
     * GET /internal/tenant-domain-subscriptions[?domainKey=][&tenantId=]
     * Returns all ACTIVE tenant↔domain subscriptions, optionally filtered to a
     * single {@code domainKey} and/or {@code tenantId} (both compose with AND).
     *
     * <p>TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): {@code tenantId} reverse-lookup
     * backs the auth-service issuance-time {@code entitled_domains} claim populate.
     */
    @GetMapping
    public ResponseEntity<SubscriptionListResponse> listSubscriptions(
            @RequestParam(required = false) String domainKey,
            @RequestParam(required = false) String tenantId) {
        List<TenantDomainSubscriptionResult> results = queryUseCase.listActive(domainKey, tenantId);
        List<SubscriptionItem> items = results.stream()
                .map(r -> new SubscriptionItem(r.tenantId(), r.domainKey()))
                .toList();
        return ResponseEntity.ok(new SubscriptionListResponse(items));
    }

    // ---- DTOs ----------------------------------------------------------------

    public record SubscriptionItem(
            String tenantId,
            String domainKey
    ) {}

    public record SubscriptionListResponse(
            List<SubscriptionItem> items
    ) {}
}
