package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.application.ActorContext;
import com.example.fanplatform.membership.application.exception.BillingKeyEnrollmentNotFoundException;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancel (turn off) auto-renewal for a tier — soft {@code active=false} (ADR-002
 * §D1; the row is kept for re-activation history). 404 if there is no active
 * enrollment for the caller + tier. Does NOT touch the membership itself: an
 * already-active membership stays valid until its window ends; it just will not
 * auto-renew.
 */
@Service
@RequiredArgsConstructor
public class CancelBillingKeyEnrollmentUseCase {

    private final BillingKeyEnrollmentRepository enrollmentRepository;

    @Transactional
    public BillingKeyEnrollmentView execute(ActorContext actor, MembershipTier tier) {
        BillingKeyEnrollment enrollment = enrollmentRepository
                .findActiveByAccountAndTier(actor.accountId(), actor.tenantId(), tier)
                .orElseThrow(() -> new BillingKeyEnrollmentNotFoundException(tier.name()));
        enrollment.deactivate();
        BillingKeyEnrollment saved = enrollmentRepository.save(enrollment);
        return BillingKeyEnrollmentView.from(saved);
    }
}
