package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-621 — a site operator locks a person out of ONE consumer site (owner decision 2026-10-04 «사이트
 * 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다»): the membership becomes LOCKED, unlock makes it
 * ACTIVE again, and neither consent nor the person's own «leave» is a way out of it.
 */
@DisplayName("TASK-BE-621 — 사이트 멤버십 LOCKED (사이트 운영자 잠금 · 해제)")
class ConsumerSiteMembershipLockTest {

    private static final TenantId STORE = new TenantId("ecommerce");
    private static final Instant JOINED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LOCKED_AT = Instant.parse("2026-10-04T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-05T00:00:00Z");

    private static ConsumerSiteMembership active() {
        return ConsumerSiteMembership.joinOnSignup("acc-1", STORE, JOINED);
    }

    private static ConsumerSiteMembership locked() {
        return active().lockBySiteOperator("op-store", LOCKED_AT);
    }

    @Test
    @DisplayName("ACTIVE → 잠금 → LOCKED · 시각 · 운영자 기록 · consented_at 보존 · 동의로 다시 열 수 없다")
    void lock_activeBecomesLocked() {
        ConsumerSiteMembership m = locked();

        assertThat(m.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LOCKED);
        assertThat(m.isLocked()).isTrue();
        assertThat(m.isActive()).isFalse();
        assertThat(m.getLockedAt()).isEqualTo(LOCKED_AT);
        assertThat(m.getLockedByActorId()).isEqualTo("op-store");
        assertThat(m.getConsentedAt()).isEqualTo(JOINED);
        assertThat(m.getLeftBy()).isNull();
        assertThat(m.isReopenableByConsent()).isFalse();
        assertThatThrownBy(() -> m.rejoinOnConsent(LATER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 LOCKED 를 다시 잠금 → 그대로(같은 객체 · 첫 잠금 기록 유지) — 멱등")
    void lock_isIdempotent() {
        ConsumerSiteMembership m = locked();

        assertThat(m.lockBySiteOperator("op-other", LATER)).isSameAs(m);
    }

    @Test
    @DisplayName("해제 → ACTIVE · 잠금 기록 지움 · consented_at 그대로 / ACTIVE 해제 → 그대로(멱등)")
    void unlock_lockedBecomesActive_activeIsNoOp() {
        ConsumerSiteMembership back = locked().unlockBySiteOperator();
        ConsumerSiteMembership a = active();

        assertThat(back.isActive()).isTrue();
        assertThat(back.getLockedAt()).isNull();
        assertThat(back.getLockedByActorId()).isNull();
        assertThat(back.getConsentedAt()).isEqualTo(JOINED);
        assertThat(a.unlockBySiteOperator()).isSameAs(a);
    }

    @Test
    @DisplayName("잠긴 사람의 본인 «탈퇴» → 그대로 LOCKED — 떠났다 다시 동의해 잠금을 벗는 길 없음")
    void selfLeave_ofLocked_isNoOp() {
        ConsumerSiteMembership m = locked();

        assertThat(m.leave(ConsumerSiteLeftBy.SELF, "acc-1", LATER)).isSameAs(m);
    }

    @Test
    @DisplayName("잠긴 회원을 운영자가 내보냄(GDPR) → LEFT · OPERATOR · 잠금 기록 지움 · 동의로 안 열림")
    void operatorLeave_ofLocked_becomesLeftOperator() {
        ConsumerSiteMembership left = locked().leave(ConsumerSiteLeftBy.OPERATOR, "op-store", LATER);

        assertThat(left.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LEFT);
        assertThat(left.getLeftBy()).isEqualTo(ConsumerSiteLeftBy.OPERATOR);
        assertThat(left.getLockedAt()).isNull();
        assertThat(left.getLockedByActorId()).isNull();
        assertThat(left.isReopenableByConsent()).isFalse();
    }

    @Test
    @DisplayName("LEFT 는 잠그거나 풀 수 없다(그 사이트에 잠글 것이 없다)")
    void left_cannotBeLockedOrUnlocked() {
        ConsumerSiteMembership left = active().leave(ConsumerSiteLeftBy.SELF, "acc-1", LATER);

        assertThatThrownBy(() -> left.lockBySiteOperator("op-store", LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(left::unlockBySiteOperator).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("V0033 CHECK 와 같은 불변식: LOCKED 는 잠금 시각 필수 · 다른 상태는 잠금 기록 금지 · LOCKED 는 탈퇴 기록 금지")
    void invariants_matchTheDatabaseChecks() {
        assertThatThrownBy(() -> ConsumerSiteMembership.reconstitute("acc-1", STORE,
                ConsumerSiteMembershipStatus.LOCKED, JOINED, null, null, null, null, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsumerSiteMembership.reconstitute("acc-1", STORE,
                ConsumerSiteMembershipStatus.ACTIVE, JOINED, null, null, null, LOCKED_AT, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConsumerSiteMembership.reconstitute("acc-1", STORE,
                ConsumerSiteMembershipStatus.LOCKED, JOINED, LATER, ConsumerSiteLeftBy.OPERATOR, "op", LOCKED_AT, "op"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ConsumerSiteMembership.reconstitute("acc-1", STORE,
                ConsumerSiteMembershipStatus.LOCKED, JOINED, null, null, null, LOCKED_AT, "op").isLocked()).isTrue();
    }
}
