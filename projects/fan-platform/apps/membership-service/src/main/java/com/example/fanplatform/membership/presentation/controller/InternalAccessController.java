package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.CheckAccessUseCase;
import com.example.fanplatform.membership.presentation.dto.AccessResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal access-check endpoint — the remote counterpart of community-service's
 * port {@code MembershipChecker.hasAccess(String accountId, String tier, String
 * tenantId)} (architecture.md § Internal Access-Check Contract).
 *
 * <p><strong>1:1 mapping.</strong> The three query parameters
 * ({@code accountId}, {@code tier}, {@code tenantId}) correspond exactly, in name
 * and meaning, to the three parameters of {@code MembershipChecker.hasAccess}, and
 * the response field {@code allowed} corresponds exactly to its boolean return
 * value — including the fail-closed (deny-on-error) contract delegated to
 * {@link CheckAccessUseCase#hasAccess}. The FAN-BE-010 {@code HttpMembershipChecker}
 * adapter is therefore a drop-in replacement for the v1
 * {@code AlwaysAllowMembershipChecker}.
 *
 * <p>A domain "deny" is NOT an error — it returns 200 with {@code allowed=false}.
 * Authentication is workload-identity ({@code client_credentials} JWT) via the
 * Order(1) {@code /internal/**} security chain (ADR-MONO-005). NOT gateway-routed.
 */
@RestController
@RequiredArgsConstructor
public class InternalAccessController {

    private final CheckAccessUseCase checkAccessUseCase;

    @GetMapping("/internal/membership/access")
    public AccessResponse access(@RequestParam("accountId") String accountId,
                                 @RequestParam("tier") String tier,
                                 @RequestParam("tenantId") String tenantId) {
        boolean allowed = checkAccessUseCase.hasAccess(accountId, tier, tenantId);
        return new AccessResponse(allowed);
    }
}
