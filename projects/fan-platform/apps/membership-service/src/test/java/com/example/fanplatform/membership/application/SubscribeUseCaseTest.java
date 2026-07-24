package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.IdempotencyKeyConflictException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import com.example.fanplatform.membership.domain.time.ClockPort;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    @Mock ClockPort clock;

    @InjectMocks SubscribeUseCase useCase;

    private SubscribeCommand cmd(String key) {
        return new SubscribeCommand(ACTOR, MembershipTier.PREMIUM, 1, "tok_visa_demo", key);
    }

    @Test
    @DisplayName("new key + approve → ACTIVE membership + idempotency row + activated event")
    void approveCreatesActive() {
        when(clock.now()).thenReturn(NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-1")).thenReturn(Optional.empty());
        when(paymentGateway.authorize(anyLong(), anyInt(), eq("tok_visa_demo"), eq("key-1")))
                .thenReturn(PaymentGatewayPort.PaymentResult.approved("pgmock_ref"));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipView view = useCase.execute(cmd("key-1"));

        assertThat(view.status().name()).isEqualTo("ACTIVE");
        assertThat(view.paymentRef()).isEqualTo("pgmock_ref");
        assertThat(view.validFrom()).isEqualTo(NOW);
        verify(membershipRepository).save(any(Membership.class));
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
        verify(eventPublisher).publishActivated(anyString(), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.PREMIUM), eq(1), eq(NOW), any(Instant.class), eq(NOW));
    }

    @Test
    @DisplayName("decline → 422 PaymentDeclined, NO row, NO idempotency row, NO event")
    void declineCreatesNoRow() {
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-2")).thenReturn(Optional.empty());
        when(paymentGateway.authorize(anyLong(), anyInt(), eq("tok_decline"), eq("key-2")))
                .thenReturn(PaymentGatewayPort.PaymentResult.declined());

        SubscribeCommand declineCmd = new SubscribeCommand(
                ACTOR, MembershipTier.PREMIUM, 1, "tok_decline", "key-2");

        assertThatThrownBy(() -> useCase.execute(declineCmd))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(membershipRepository, never()).save(any());
        verify(idempotencyKeyRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(anyString(), anyString(), anyString(),
                any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("replay (same key + same payload) → stored membership, NO re-auth, NO dup row")
    void idempotentReplay() {
        when(clock.now()).thenReturn(NOW);
        Membership stored = Membership.activate(
                "m-stored", "fan-platform", "acc1", MembershipTier.PREMIUM,
                NOW, NOW.plusSeconds(100), 1, "pgmock_ref", NOW);
        // Same fingerprint as cmd("key-1") payload (PREMIUM|1|tok_visa_demo).
        IdempotencyKey key = IdempotencyKey.create("fan-platform", "acc1", "key-1",
                fingerprintOf(MembershipTier.PREMIUM, 1, "tok_visa_demo"), "m-stored", NOW);
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "key-1")).thenReturn(Optional.of(key));
        when(membershipRepository.findByIdScoped("m-stored", "acc1", "fan-platform"))
                .thenReturn(Optional.of(stored));

        MembershipView view = useCase.execute(cmd("key-1"));

        assertThat(view.membershipId()).isEqualTo("m-stored");
        verify(paymentGateway, never()).authorize(anyLong(), anyInt(), anyString(), anyString());
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

        verify(paymentGateway, never()).authorize(anyLong(), anyInt(), anyString(), anyString());
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
