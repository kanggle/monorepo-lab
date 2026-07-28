package com.example.fanplatform.membership.application.billing;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enroll a billing key for a tier (ADR-002 §D1). At most one ACTIVE enrollment per
 * (account, tenant, tier): an existing active enrollment for the same tier is
 * <b>soft-deactivated and replaced</b> (never stacked) so a fan is never charged
 * twice for one tier. The "one active" invariant is also enforced at the DB level
 * by the partial unique index {@code uq_bke_active_account_tier} — the
 * check-then-replace here is the happy path; a concurrent racer hits the index and
 * surfaces as a 409 (integrity) rather than a second chargeable row.
 *
 * <p>The billing key is trusted as-is (ADR-MONO-057 §7 — Phase 1 does no
 * server-side issuance verification; the blast radius is bounded by the charge-time
 * {@code verify}). It is encrypted at rest by the entity's converter and NEVER
 * logged here.
 */
@Service
@RequiredArgsConstructor
public class EnrollBillingKeyUseCase {

    private final BillingKeyEnrollmentRepository enrollmentRepository;
    private final ClockPort clock;

    @Transactional
    public BillingKeyEnrollmentView execute(EnrollBillingKeyCommand cmd) {
        String tenantId = cmd.actor().tenantId();
        String accountId = cmd.actor().accountId();

        // Replace any existing active enrollment for this tier: an IMMEDIATE bulk
        // deactivation (runs now, before the insert flush) so the partial unique index
        // never sees two active rows (Hibernate flushes inserts before updates).
        enrollmentRepository.deactivateActive(accountId, tenantId, cmd.tier());

        BillingKeyEnrollment enrollment = BillingKeyEnrollment.enroll(
                UuidV7.randomString(), tenantId, accountId, cmd.tier(),
                cmd.billingKey(), clock.now());
        BillingKeyEnrollment saved = enrollmentRepository.save(enrollment);
        return BillingKeyEnrollmentView.from(saved);
    }
}
