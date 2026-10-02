package com.example.product.domain.model;

import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationExpiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-MONO-752 — invitation single use + expiry, at the boundary. */
@DisplayName("SellerInvitation — 1회 · 만료 (TASK-MONO-752)")
class SellerInvitationTest {

    private static final Instant T0 = Instant.parse("2026-10-03T00:00:00Z");

    private static SellerInvitation pending() {
        return SellerInvitation.issue("s-1", " A@B.co ", "hash", "op", T0, Duration.ofDays(7));
    }

    @Test
    @DisplayName("만료 1ns 전은 받고, 만료 시각 정각부터 거절")
    void expiryBoundary() {
        SellerInvitation inv = pending();
        assertThat(inv.getEmail()).isEqualTo("a@b.co");
        assertThat(inv.alreadyAcceptedBy("acct", T0.plus(Duration.ofDays(7)).minusNanos(1))).isFalse();
        assertThatThrownBy(() -> inv.alreadyAcceptedBy("acct", T0.plus(Duration.ofDays(7))))
                .isInstanceOf(SellerInvitationExpiredException.class);
    }

    @Test
    @DisplayName("수락된 초대 — 같은 계정은 이중 제출(true), 다른 계정은 재사용 거절 · 만료 뒤에도 같은 답")
    void acceptedInvitation() {
        SellerInvitation inv = SellerInvitation.reconstitute("i", "s-1", "a@b.co", "hash",
                SellerInvitationStatus.ACCEPTED, T0.plusSeconds(1), "op", T0, T0, "acct");
        assertThat(inv.alreadyAcceptedBy("acct", T0.plus(Duration.ofDays(30)))).isTrue();
        assertThatThrownBy(() -> inv.alreadyAcceptedBy("other", T0))
                .isInstanceOf(SellerInvitationAlreadyUsedException.class);
    }

    @Test
    @DisplayName("이메일 없음 · 수명 0 → 발급 불가")
    void invalidIssue() {
        assertThatThrownBy(() -> SellerInvitation.issue("s-1", " ", "h", "op", T0, Duration.ofDays(7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SellerInvitation.issue("s-1", "a@b.co", "h", "op", T0, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
