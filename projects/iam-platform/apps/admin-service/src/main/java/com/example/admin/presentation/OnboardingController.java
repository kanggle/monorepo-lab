package com.example.admin.presentation;

import com.example.admin.application.SelfServiceOnboardingUseCase;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.presentation.aspect.SelfServiceEndpoint;
import com.example.admin.presentation.dto.OnboardOrganizationRequest;
import com.example.admin.presentation.dto.OnboardOrganizationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-BE-474 (ADR-MONO-044 D7) — the self-service B2B tenant onboarding surface.
 *
 * <p><b>Public but authenticated, NOT operator-gated.</b> This is the one admin-service
 * mutation that a non-operator may call: the caller is an ordinary authenticated IAM
 * user (not an operator), so the endpoint is on the {@code permitAll} list, is skipped
 * by {@code OperatorAuthenticationFilter} (no operator JWT), and is annotated
 * {@link SelfServiceEndpoint} so the deny-by-default RBAC guardrail admits it. It
 * validates the caller's OWN OIDC token (ADR-014 {@code IamOidcSubjectTokenValidator},
 * auth-service JWKS, {@code platform-console-web} audience) and resolves the caller's
 * email/name from the AUTHORITATIVE account — never trusting the request body for identity.
 *
 * <p>Downstream errors surface via the existing {@code @RestControllerAdvice}:
 * {@code SubjectTokenInvalidException} → 401, {@code TenantAlreadyExistsException} → 409,
 * {@code MethodArgumentNotValidException} (bad slug) → 400, downstream failures → 5xx.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final IamOidcSubjectTokenValidator subjectTokenValidator;
    private final AccountServiceClient accountServiceClient;
    private final SelfServiceOnboardingUseCase onboardingUseCase;

    /**
     * POST /api/admin/onboarding/organizations — create a new tenant and become its
     * first {@code TENANT_ADMIN} + {@code TENANT_BILLING_ADMIN} (ADR-044 D1).
     */
    @PostMapping("/organizations")
    @SelfServiceEndpoint
    public ResponseEntity<OnboardOrganizationResponse> onboardOrganization(
            @Valid @RequestBody OnboardOrganizationRequest request) {

        // 1. Authenticate the caller's OWN token → account_id (sub). Fail-closed on any
        //    invalidity (SubjectTokenInvalidException → 401); rejects operator/bootstrap
        //    tokens (they carry token_type). ADR-014 validator, auth-service JWKS.
        String accountId = subjectTokenValidator.validateAndExtractSubject(request.subjectToken());

        // 2. Resolve the caller's email + display name from the AUTHORITATIVE account
        //    (never from the request body). The email keys the born-unified identity + operator.
        AccountServiceClient.AccountDetailResponse account = accountServiceClient.getDetail(accountId);
        String email = account.email();
        String displayName = (account.profile() != null && account.profile().displayName() != null
                && !account.profile().displayName().isBlank())
                ? account.profile().displayName()
                : email;

        // 3. Orchestrate: create tenant → mint first admin (TENANT_ADMIN + TENANT_BILLING_ADMIN
        //    scoped to the new tenant) → assignment. Fail-closed with compensation (D3).
        SelfServiceOnboardingUseCase.Result result =
                onboardingUseCase.onboard(request.tenantId(), request.organizationName(), email, displayName);

        return ResponseEntity.status(HttpStatus.CREATED).body(OnboardOrganizationResponse.from(result));
    }
}
