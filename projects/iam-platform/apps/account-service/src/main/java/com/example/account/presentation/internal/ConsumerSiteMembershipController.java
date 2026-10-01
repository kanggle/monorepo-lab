package com.example.account.presentation.internal;

import com.example.account.application.service.ConsentToConsumerSiteUseCase;
import com.example.account.application.service.GetConsumerSiteMembershipUseCase;
import com.example.account.presentation.dto.response.ConsumerSiteMembershipResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-615 (ADR-MONO-078 A) — {@code GET /internal/tenants/{tenantId}/consumer-members/{accountId}}:
 * a pool account's membership of ONE consumer site and its site roles there. Caller: auth-service
 * (form login, {@code /oauth2/authorize}, token issuance). Contract:
 * {@code specs/contracts/http/internal/auth-to-account.md}.
 *
 * <p>TASK-BE-616 — {@code PUT} on the same resource: the first-visit consent. Idempotent: creates the
 * ACTIVE membership (and the site's {@code account.created}) only when there is none, and answers with
 * the same body as the read in every case.
 *
 * <p>Site-first path (the site is the scope, like {@code /internal/tenants/{tenantId}/accounts/{id}/roles}).
 * Authentication: workload JWT on {@code /internal/**} (SecurityConfig). Tenant scope: an
 * {@code X-Tenant-Id} that names another tenant is refused ({@link TenantScopeGuard}); auth-service
 * sends none.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/tenants/{tenantId}/consumer-members")
public class ConsumerSiteMembershipController {

    private final GetConsumerSiteMembershipUseCase getConsumerSiteMembershipUseCase;
    private final ConsentToConsumerSiteUseCase consentToConsumerSiteUseCase;

    @GetMapping("/{accountId}")
    public ResponseEntity<ConsumerSiteMembershipResponse> get(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId) {

        TenantScopeGuard.validate(callerTenantId, tenantId);
        return ResponseEntity.ok(ConsumerSiteMembershipResponse.from(
                getConsumerSiteMembershipUseCase.execute(tenantId, accountId)));
    }

    /**
     * TASK-BE-616 — the consent write. Always 200 with the read's body: {@code membershipStatus = ACTIVE}
     * means the account may now use the site; anything else (not a consumer site, a suspended site, not a
     * pool account, a LEFT membership) is an answer, not an error, and nothing was written.
     *
     * <p>Two concurrent first consents for the same {@code (account, site)} collide on the primary key.
     * The loser's transaction — its membership row and its {@code account.created} — rolls back as a
     * whole; it then answers with the read, which sees the winner's ACTIVE row. Exactly one event.
     */
    @PutMapping("/{accountId}")
    public ResponseEntity<ConsumerSiteMembershipResponse> consent(
            @PathVariable String tenantId,
            @PathVariable String accountId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String callerTenantId) {

        TenantScopeGuard.validate(callerTenantId, tenantId);
        try {
            return ResponseEntity.ok(ConsumerSiteMembershipResponse.from(
                    consentToConsumerSiteUseCase.execute(tenantId, accountId)));
        } catch (DataIntegrityViolationException e) {
            log.info("consumer-site consent: concurrent first consent for account {} on site {} — "
                    + "answering with the existing membership", accountId, tenantId);
            return ResponseEntity.ok(ConsumerSiteMembershipResponse.from(
                    getConsumerSiteMembershipUseCase.execute(tenantId, accountId)));
        }
    }
}
