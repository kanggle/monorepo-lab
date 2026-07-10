package com.example.account.presentation.internal;

import com.example.account.application.service.TenantEntitledDomainsQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TASK-BE-491 (ADR-MONO-047 § D6): the tenant's <b>effective</b> entitled domains —
 * {@code ACTIVE subscriptions ∩ effectiveCeiling(tenant)}.
 *
 * <p>This is the single point at which the org-node ceiling is enforced. auth-service's
 * {@code AccountServiceClient} targets this endpoint to populate the signed
 * {@code entitled_domains} claim; {@code TenantClaimTokenCustomizer} is byte-unchanged.
 *
 * <p>Deliberately separate from {@code GET /internal/tenant-domain-subscriptions}, which
 * still returns the RAW ACTIVE rows for the console catalog and subscription management. A
 * security-critical endpoint whose semantics flip on a query parameter is a footgun, so the
 * narrowed view gets its own name.
 *
 * <p>An empty {@code domainKeys} is legal and means the caller must fail closed.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants")
public class TenantEntitledDomainsController {

    private final TenantEntitledDomainsQueryUseCase queryUseCase;

    @GetMapping("/{tenantId}/entitled-domains")
    public ResponseEntity<EntitledDomainsResponse> entitledDomains(@PathVariable String tenantId) {
        return ResponseEntity.ok(new EntitledDomainsResponse(
                tenantId, queryUseCase.effectiveEntitledDomains(tenantId)));
    }

    public record EntitledDomainsResponse(String tenantId, List<String> domainKeys) {}
}
