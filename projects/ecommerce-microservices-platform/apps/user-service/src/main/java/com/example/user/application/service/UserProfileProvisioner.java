package com.example.user.application.service;

import com.example.user.domain.model.UserProfile;
import com.example.user.domain.repository.UserProfileRepository;
import com.example.user.domain.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single place a {@code user_profiles} row is born (TASK-BE-575).
 *
 * <h2>Why this exists</h2>
 *
 * <p>Since ADR-MONO-040 the identity is IAM's; the ecommerce profile is the domain
 * <em>projection</em> of that identity. The projection was supposed to be created by
 * {@link AccountCreatedHandler} reacting to IAM's {@code account.created}
 * (ADR-MONO-037 P1) — and that consumer is correct. It just never receives anything in
 * the per-project compose topology: IAM publishes to the {@code iam-platform} Kafka
 * cluster and ecommerce's consumers subscribe to the {@code ecommerce} one. Measured
 * (TASK-BE-575 AC-0): a real browser signup moved {@code iam-kafka}'s
 * {@code account.created} log-end-offset 0 → 1 while {@code ecommerce-kafka}'s stayed
 * at 0, and no profile appeared. The IAM consumer-integration guide assumes the
 * consumer shares IAM's cluster ("단일 Kafka 클러스터를 다수 테넌트가 공유"), which the
 * split compose does not provide. That topology gap is TASK-MONO-511 — it is not this
 * class's business, and it is not only about onboarding ({@code account.deleted}, which
 * carries the TASK-BE-258 GDPR anonymization obligation, is dead by the same mechanism).
 *
 * <p>So the projection needs a second, always-available source. The same IAM guide
 * names one: when the event stream is unavailable, consumers fall back to
 * <b>pull-through</b> — deriving the projection from what the caller already carries
 * rather than from the event. That is what this class does, from the gateway-verified
 * identity headers, and it is why the fix needs no IAM change.
 *
 * <h2>Both paths converge here</h2>
 *
 * <p>{@link AccountCreatedHandler} (event) and {@link com.example.user.presentation.filter.UserProfileProvisioningFilter}
 * (pull-through) both call {@link #ensureProvisioned}, so a profile born from an event
 * and a profile born from a first request are byte-identical, and whichever arrives
 * first wins with the other becoming a no-op. Restoring the event path later changes
 * nothing here.
 *
 * <h2>The shape is deliberately minimal</h2>
 *
 * <p>{@code email}/{@code name} are left null exactly as {@link UserProfile#createMinimal}
 * leaves them for the event path (ADR-MONO-037 P5/P6: nothing may depend on them being
 * non-null; PII is sourced later). The caller may pass an email when the edge supplies
 * one — but note that today it never does: ecommerce's gateway maps
 * {@code X-User-Email} with {@code skipIfNull(JwtClaims::email)} and the SAS access
 * token carries no {@code email} claim (measured, TASK-BE-575 AC-0), so the header is
 * absent and the argument arrives null. The wire is honoured rather than assumed —
 * the day the claim appears, profiles start carrying it with no further change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileProvisioner {

    private final UserProfileRepository userProfileRepository;

    /**
     * Ensure a profile exists for this IAM subject in the current tenant. Idempotent.
     *
     * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so that provisioning from
     * the request filter commits independently of whatever the request goes on to do —
     * a later failure in the handler must not roll the projection back and leave the
     * next request to re-provision.
     *
     * <p><b>The two reads are not redundant.</b> {@code findByUserId} is tenant-scoped
     * (it filters on {@link TenantContext}), while the {@code uq_user_profiles_user_id}
     * unique constraint — and therefore {@code existsByUserId} — is on {@code user_id}
     * <em>alone</em>, globally. A row can exist for this subject under a different
     * tenant: the tenant-scoped read misses it, but the insert would violate the unique
     * index. Deciding on {@code existsByUserId} alone would instead skip silently and
     * leave the caller permanently 404 with nothing said. So: tenant-scoped read first
     * (one indexed lookup, the steady-state cost), and only on a miss do we ask the
     * global question and name the cross-tenant case out loud.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureProvisioned(UUID userId, String email) {
        if (userId == null) {
            return;
        }
        if (userProfileRepository.findByUserId(userId).isPresent()) {
            return;
        }
        if (userProfileRepository.existsByUserId(userId)) {
            log.warn("UserProfile exists for userId={} but not in tenant={} — not provisioning "
                            + "(uq_user_profiles_user_id is global while reads are tenant-scoped). "
                            + "The caller will see USER_PROFILE_NOT_FOUND; this line is why.",
                    userId, TenantContext.currentTenant());
            return;
        }

        UserProfile profile = newProfile(userId, email);
        try {
            userProfileRepository.save(profile);
            log.info("Provisioned UserProfile for userId={} tenant={} (pull-through from the "
                    + "gateway-verified identity; email/name sourced later)", userId, TenantContext.currentTenant());
        } catch (DataIntegrityViolationException e) {
            // Two concurrent first-requests from the same subject: the loser lands here.
            // The row exists, which is all the caller needed — not an error.
            log.debug("Concurrent provisioning for userId={}, losing insert ignored", userId);
        }
    }

    /**
     * A profile carrying the edge-supplied email when there is one, otherwise the same
     * minimal shape the event path creates. A malformed value is not worth failing the
     * caller's request over — the email is an optional enrichment of a projection whose
     * identity is the {@code userId}, so we drop it and create the minimal profile.
     */
    private UserProfile newProfile(UUID userId, String email) {
        if (email == null || email.isBlank()) {
            return UserProfile.createMinimal(userId);
        }
        try {
            return UserProfile.create(userId, email.trim(), null);
        } catch (IllegalArgumentException e) {
            log.warn("Edge supplied an unusable X-User-Email for userId={}, provisioning minimal profile", userId);
            return UserProfile.createMinimal(userId);
        }
    }
}
