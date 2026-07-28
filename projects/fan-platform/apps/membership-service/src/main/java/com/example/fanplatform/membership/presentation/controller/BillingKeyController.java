package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.billing.BillingKeyEnrollmentView;
import com.example.fanplatform.membership.application.billing.CancelBillingKeyEnrollmentUseCase;
import com.example.fanplatform.membership.application.billing.EnrollBillingKeyCommand;
import com.example.fanplatform.membership.application.billing.EnrollBillingKeyUseCase;
import com.example.fanplatform.membership.application.exception.MembershipTierInvalidException;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.presentation.dto.BillingKeyCancelResponse;
import com.example.fanplatform.membership.presentation.dto.BillingKeyEnrollmentResponse;
import com.example.fanplatform.membership.presentation.dto.EnrollBillingKeyRequest;
import com.example.fanplatform.membership.presentation.security.CurrentActor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Billing-key enrollment endpoints (ADR-002 §D1/§D2) under
 * {@code /api/fan/memberships/billing-key} — the end-user chain ({@code /api/fan/**},
 * already {@code .authenticated()}). Plural {@code memberships} (fix-01, TASK-FAN-BE-033):
 * the gateway's existing rewrite only matches {@code /api/v1/memberships/**} →
 * {@code /api/fan/memberships${segment}} (TASK-FAN-BE-009) — a singular {@code membership}
 * path here was unreachable through the gateway. A fan registers the
 * {@code billingKey} the frontend obtained from {@code PortOne.requestIssueBillingKey(...)}
 * once; the auto-renew scheduler then charges it on the membership's renewal date.
 *
 * <p><b>The billing key is never returned</b> in any response (ADR-002 §D5) — the
 * response carries only {@code enrollmentId / tier / active / createdAt}.
 *
 * <p>Responses are flat bodies (the pinned TASK-FAN-BE-033 contract), unlike the
 * {@code /api/fan/memberships} list/detail surface which wraps in {@code { data, meta }}.
 */
@RestController
@RequestMapping("/api/fan/memberships/billing-key")
@RequiredArgsConstructor
public class BillingKeyController {

    private final EnrollBillingKeyUseCase enrollBillingKeyUseCase;
    private final CancelBillingKeyEnrollmentUseCase cancelBillingKeyEnrollmentUseCase;

    /**
     * Enroll (or replace the existing active enrollment for) a tier's billing key.
     * 201 with the new enrollment; 422 {@code MEMBERSHIP_TIER_INVALID} for an
     * unknown tier.
     */
    @PostMapping
    public ResponseEntity<BillingKeyEnrollmentResponse> enroll(
            @CurrentActor ActorContext actor,
            @Valid @RequestBody EnrollBillingKeyRequest req) {
        MembershipTier tier = parseTier(req.tier());
        EnrollBillingKeyCommand cmd = new EnrollBillingKeyCommand(actor, tier, req.billingKey());
        BillingKeyEnrollmentView view = enrollBillingKeyUseCase.execute(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(BillingKeyEnrollmentResponse.from(view));
    }

    /**
     * Turn off auto-renewal for a tier (soft deactivate). 200 with
     * {@code active:false}; 404 {@code BILLING_KEY_ENROLLMENT_NOT_FOUND} if there is
     * no active enrollment for the tier.
     */
    @DeleteMapping("/{tier}")
    public ResponseEntity<BillingKeyCancelResponse> cancel(
            @CurrentActor ActorContext actor,
            @PathVariable String tier) {
        MembershipTier parsed = parseTier(tier);
        BillingKeyEnrollmentView view = cancelBillingKeyEnrollmentUseCase.execute(actor, parsed);
        return ResponseEntity.ok(BillingKeyCancelResponse.from(view));
    }

    private static MembershipTier parseTier(String raw) {
        try {
            return MembershipTier.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MembershipTierInvalidException(raw);
        }
    }
}
