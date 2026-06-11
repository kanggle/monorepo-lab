package com.example.fanplatform.membership.application;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.application.exception.MembershipNotRenewableException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Renew (seamless re-activation). Creates a NEW membership continuing the prior
 * one's tier; the prior row is never mutated (architecture.md § State Machine —
 * no stored-state change). Mirrors {@link SubscribeUseCase}'s Idempotency-Key +
 * PG-mock + activated-outbox-in-one-TX flow, but resolves the prior membership
 * (scoped), rejects a CANCELED prior, inherits the tier, and computes a seamless
 * window.
 *
 * <p>Window: {@code validFrom = max(now, prior.validTo)} — renewing early stacks
 * onto the current window (no lost days); renewing after expiry starts at
 * {@code now}. {@code validTo = validFrom + planMonths·30d}.
 */
@Service
@RequiredArgsConstructor
public class RenewMembershipUseCase {

    /** Synthetic per-month price in minor units (mock — not externally exposed). */
    private static final long PRICE_PER_MONTH_MINOR = 9_900L;

    private final MembershipRepository membershipRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final PaymentGatewayPort paymentGateway;
    private final MembershipEventPublisher eventPublisher;
    private final ClockPort clock;

    @Transactional
    public MembershipView execute(RenewCommand cmd) {
        ActorContext actor = cmd.actor();
        String tenantId = actor.tenantId();
        String accountId = actor.accountId();

        // 1) Resolve the prior membership (scoped) — 404 if missing / foreign.
        Membership prior = membershipRepository
                .findByIdScoped(cmd.priorMembershipId(), accountId, tenantId)
                .orElseThrow(() -> new MembershipNotFoundException(cmd.priorMembershipId()));
        // A CANCELED membership is a deliberate opt-out — not renewable.
        if (prior.isCanceled()) {
            throw new MembershipNotRenewableException(cmd.priorMembershipId());
        }

        String fingerprint = fingerprint(cmd.priorMembershipId(), cmd.planMonths(), cmd.paymentToken());

        // 2) Idempotency check ---------------------------------------------------
        Optional<IdempotencyKey> existing =
                idempotencyKeyRepository.find(tenantId, accountId, cmd.idempotencyKey());
        if (existing.isPresent()) {
            IdempotencyKey key = existing.get();
            if (!key.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException(cmd.idempotencyKey());
            }
            // Same key + same payload → replay the stored renewed membership.
            Membership stored = membershipRepository
                    .findByIdScoped(key.getMembershipId(), accountId, tenantId)
                    .orElseThrow(() -> new MembershipNotFoundException(key.getMembershipId()));
            return MembershipView.from(stored, clock.now());
        }

        // 3) PG authorize --------------------------------------------------------
        long amountMinor = PRICE_PER_MONTH_MINOR * cmd.planMonths();
        PaymentGatewayPort.PaymentResult result = paymentGateway.authorize(
                amountMinor, cmd.planMonths(), cmd.paymentToken(), cmd.idempotencyKey());
        if (!result.approved()) {
            throw new PaymentDeclinedException();
        }

        // 4) Create the renewed membership (seamless window) + idempotency + event.
        Instant now = clock.now();
        Instant validFrom = prior.getValidTo().isAfter(now) ? prior.getValidTo() : now;
        Instant validTo = validFrom.plus(cmd.planMonths() * 30L, ChronoUnit.DAYS);
        String membershipId = UuidV7.randomString();

        Membership renewed = Membership.activate(
                membershipId, tenantId, accountId, prior.getTier(),
                validFrom, validTo, cmd.planMonths(), result.paymentRef(), now);
        Membership saved = membershipRepository.save(renewed);

        idempotencyKeyRepository.save(IdempotencyKey.create(
                tenantId, accountId, cmd.idempotencyKey(), fingerprint, membershipId, now));

        eventPublisher.publishActivated(
                saved.getId(), saved.getTenantId(), saved.getAccountId(),
                saved.getTier(), saved.getPlanMonths(),
                saved.getValidFrom(), saved.getValidTo(), now);

        return MembershipView.from(saved, now);
    }

    /**
     * Stable hash of the renew payload (prior id + plan + token) — a mismatch on
     * replay means the same key is being reused for a different request (409).
     * Excludes the idempotency key itself (the lookup key).
     */
    private static String fingerprint(String priorMembershipId, int planMonths, String paymentToken) {
        String raw = "renew|" + priorMembershipId + "|" + planMonths + "|"
                + (paymentToken == null ? "" : paymentToken);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
