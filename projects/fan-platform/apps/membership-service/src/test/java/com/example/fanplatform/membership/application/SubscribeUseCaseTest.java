package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.pricing.UpgradeProration;
import com.example.fanplatform.membership.domain.time.ClockPort;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SubscribeUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("acc1", "fan-platform", java.util.Set.of("FAN"));

    @Mock MembershipRepository membershipRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock PaymentGatewayPort paymentGateway;
    @Mock MembershipEventPublisher eventPublisher;
    @Mock UpgradeQuoter upgradeQuoter;
    @Mock ClockPort clock;

    @InjectMocks SubscribeUseCase useCase;

    private SubscribeCommand cmd(String key) {
        return new SubscribeCommand(ACTOR, MembershipTier.PREMIUM, 1, "tok_visa_demo", key);
    }

    /**
     * The shared PG port takes a single verification request: paymentReference (token) +
     * expectedAmountMinor + currency (always "KRW" for this service). planMonths /
     * idempotencyKey are no longer part of the PG contract (the real adapter never used them),
     * so the amount + token intent of the old 4-arg matchers is preserved via record equality.
     */
    private static PaymentVerificationRequest verifyReq(long amountMinor, String token) {
        return new PaymentVerificationRequest(token, amountMinor, "KRW", null);
    }

    /** No members-only held → plain PREMIUM list price (17,900), no supersede. */
    private void stubPlainPremiumAssessment() {
        when(upgradeQuoter.assess(eq(MembershipTier.PREMIUM), eq(1), eq("acc1"), eq("fan-platform"), any()))
                .thenReturn(new UpgradeQuoter.UpgradeAssessment(
                        Optional.empty(), new UpgradeProration.Quote(17_900, 0, 17_900, 0)));
    }

    @Test
    @DisplayName("new key + approve → ACTIVE membership + idempotency row + activated event")
    void approveCreatesActive() {
        when(clock.now()).thenReturn(NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-1")).thenReturn(Optional.empty());
        stubPlainPremiumAssessment();
        when(paymentGateway.verify(verifyReq(17_900L, "tok_visa_demo")))
                .thenReturn(PaymentAuthorization.approved("pgmock_ref", null, null));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipView view = useCase.execute(cmd("key-1"));

        assertThat(view.status().name()).isEqualTo("ACTIVE");
        assertThat(view.paymentRef()).isEqualTo("pgmock_ref");
        assertThat(view.validFrom()).isEqualTo(NOW);
        verify(paymentGateway).verify(verifyReq(17_900L, "tok_visa_demo"));
        verify(membershipRepository).save(any(Membership.class));
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
        verify(eventPublisher).publishActivated(anyString(), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.PREMIUM), eq(1), eq(NOW), any(Instant.class), eq(NOW));
    }

    @Test
    @DisplayName("PREMIUM holding active MEMBERS_ONLY → supersede + prorated charge + both events")
    void upgradeSupersedesMembersOnly() {
        when(clock.now()).thenReturn(NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-up")).thenReturn(Optional.empty());
        Membership members = Membership.activate("m-members", "fan-platform", "acc1",
                MembershipTier.MEMBERS_ONLY, NOW.minusSeconds(100), NOW.plusSeconds(1_296_000L),
                1, "pgmock_members", NOW);
        // 15 remaining days → credit 3,950, prorated charge 13,950 (see UpgradeProrationTest).
        UpgradeProration.Quote q = new UpgradeProration.Quote(17_900, 3_950, 13_950, 15);
        when(upgradeQuoter.assess(eq(MembershipTier.PREMIUM), eq(1), eq("acc1"), eq("fan-platform"), any()))
                .thenReturn(new UpgradeQuoter.UpgradeAssessment(Optional.of(members), q));
        when(paymentGateway.verify(verifyReq(13_950L, "tok_visa_demo")))
                .thenReturn(PaymentAuthorization.approved("pgmock_up", null, null));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(cmd("key-up"));

        // The members-only row is superseded (canceled) and the PORATED amount is charged.
        assertThat(members.isCanceled()).isTrue();
        verify(paymentGateway).verify(verifyReq(13_950L, "tok_visa_demo"));
        verify(eventPublisher).publishCanceled(eq("m-members"), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.MEMBERS_ONLY), eq("SUPERSEDED_BY_UPGRADE"), any(Instant.class), any(Instant.class));
        verify(eventPublisher).publishActivated(anyString(), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.PREMIUM), eq(1), any(Instant.class), any(Instant.class), any(Instant.class));
        // members-only save (cancel) + premium save.
        verify(membershipRepository, times(2)).save(any(Membership.class));
    }

    @Test
    @DisplayName("decline → 422 PaymentDeclined, NO row, NO idempotency row, NO event, NO supersede")
    void declineCreatesNoRow() {
        when(clock.now()).thenReturn(NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-2")).thenReturn(Optional.empty());
        when(upgradeQuoter.assess(eq(MembershipTier.PREMIUM), eq(1), eq("acc1"), eq("fan-platform"), any()))
                .thenReturn(new UpgradeQuoter.UpgradeAssessment(
                        Optional.empty(), new UpgradeProration.Quote(17_900, 0, 17_900, 0)));
        when(paymentGateway.verify(verifyReq(17_900L, "tok_decline")))
                .thenReturn(PaymentAuthorization.declined());

        SubscribeCommand declineCmd = new SubscribeCommand(
                ACTOR, MembershipTier.PREMIUM, 1, "tok_decline", "key-2");

        assertThatThrownBy(() -> useCase.execute(declineCmd))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(membershipRepository, never()).save(any());
        verify(idempotencyKeyRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(anyString(), anyString(), anyString(),
                any(), anyInt(), any(), any(), any());
        verify(eventPublisher, never()).publishCanceled(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("replay (same key + same payload) → stored membership, NO re-auth, NO dup row")
    void idempotentReplay() {
        when(clock.now()).thenReturn(NOW);
        Membership stored = Membership.activate(
                "m-stored", "fan-platform", "acc1", MembershipTier.PREMIUM,
                NOW, NOW.plusSeconds(100), 1, "pgmock_ref", NOW);
        IdempotencyKey key = IdempotencyKey.create("fan-platform", "acc1", "key-1",
                fingerprintOf(MembershipTier.PREMIUM, 1, "tok_visa_demo"), "m-stored", NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-1")).thenReturn(Optional.of(key));
        when(membershipRepository.findByIdScoped("m-stored", "acc1", "fan-platform"))
                .thenReturn(Optional.of(stored));

        MembershipView view = useCase.execute(cmd("key-1"));

        assertThat(view.membershipId()).isEqualTo("m-stored");
        verify(paymentGateway, never()).verify(any());
        verify(membershipRepository, never()).save(any());
        verify(idempotencyKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("reuse key with different payload → 409 IdempotencyKeyConflict")
    void idempotencyConflict() {
        IdempotencyKey key = IdempotencyKey.create("fan-platform", "acc1", "key-1",
                fingerprintOf(MembershipTier.MEMBERS_ONLY, 3, "other"), "m-old", NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-1")).thenReturn(Optional.of(key));

        assertThatThrownBy(() -> useCase.execute(cmd("key-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        verify(paymentGateway, never()).verify(any());
        verify(membershipRepository, never()).save(any());
    }

    /** Mirror of SubscribeUseCase#fingerprint (SHA-256 of tier|planMonths|paymentId). */
    private static String fingerprintOf(MembershipTier tier, int planMonths, String token) {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA-256");
            String raw = tier.name() + "|" + planMonths + "|" + (token == null ? "" : token);
            return java.util.HexFormat.of().formatHex(
                    d.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
