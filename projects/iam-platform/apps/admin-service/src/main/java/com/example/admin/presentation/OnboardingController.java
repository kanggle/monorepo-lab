package com.example.admin.presentation;

import com.example.admin.application.OnboardingCallerAccount;
import com.example.admin.application.ReceivedInvitationsQueryUseCase;
import com.example.admin.application.SelfServiceOnboardingUseCase;
import com.example.admin.application.exception.NonRetryableDownstreamException;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.presentation.aspect.SelfServiceEndpoint;
import com.example.admin.presentation.dto.OnboardOrganizationRequest;
import com.example.admin.presentation.dto.OnboardOrganizationResponse;
import com.example.admin.presentation.dto.ReceivedInvitationsRequest;
import com.example.admin.presentation.dto.ReceivedInvitationsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

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
 * <p>TASK-MONO-773 S3: the account read's {@code 404} is no longer a failure — it is the trust gate's input
 * (owner decision option A: an {@code iam}-credential operator has no account record). The gate itself runs in the
 * use case. {@code POST …/received-invitations} (OD-7) is the same self flow, read-only.
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
    private final ReceivedInvitationsQueryUseCase receivedInvitationsUseCase;

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
        //    TASK-MONO-773 S3: an absent record is the gate's input, not an error (option A).
        Optional<OnboardingCallerAccount> account = readAuthoritativeAccount(accountId);

        // 3. Orchestrate: trust gate → create tenant → mint first admin (TENANT_ADMIN + TENANT_BILLING_ADMIN
        //    scoped to the new tenant) → assignment → ORIGIN set. Fail-closed with compensation (D3).
        SelfServiceOnboardingUseCase.Result result =
                onboardingUseCase.onboard(request.tenantId(), request.organizationName(), accountId, account);

        return ResponseEntity.status(HttpStatus.CREATED).body(OnboardOrganizationResponse.from(result));
    }

    /**
     * TASK-MONO-773 OD-7 — POST /api/admin/onboarding/received-invitations: the invitations addressed to the
     * caller's verified email. Read-only (a POST only because the subject token rides in the body).
     */
    @PostMapping("/received-invitations")
    @SelfServiceEndpoint
    public ResponseEntity<ReceivedInvitationsResponse> receivedInvitations(
            @Valid @RequestBody ReceivedInvitationsRequest request) {
        String accountId = subjectTokenValidator.validateAndExtractSubject(request.subjectToken());
        Optional<OnboardingCallerAccount> account = readAuthoritativeAccount(accountId);
        return ResponseEntity.ok(ReceivedInvitationsResponse.from(receivedInvitationsUseCase.read(accountId, account)));
    }

    /**
     * account-service {@code GET /internal/accounts/{sub}}: {@code 200} → the record; {@code 404} → empty (no
     * account record — the gate decides); anything else propagates (fail-closed, 5xx).
     */
    private Optional<OnboardingCallerAccount> readAuthoritativeAccount(String accountId) {
        AccountServiceClient.AccountDetailResponse detail;
        try {
            detail = accountServiceClient.getDetail(accountId);
        } catch (NonRetryableDownstreamException e) {
            if (e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            throw e;
        }
        if (detail == null || detail.email() == null || detail.email().isBlank()) {
            // A 200 without the record is not «no account» — never let it reach the exemption (fail-closed, 503).
            throw new com.example.admin.application.exception.DownstreamFailureException(
                    "account-service answered without the account record", null);
        }
        String email = detail.email();
        String displayName = (detail.profile() != null && detail.profile().displayName() != null
                && !detail.profile().displayName().isBlank())
                ? detail.profile().displayName()
                : email;
        return Optional.of(new OnboardingCallerAccount(email, displayName));
    }
}
