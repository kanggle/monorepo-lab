package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-619 — leaving one consumer site and coming back (owner decisions 2026-10-03: «사이트 운영자 삭제 권한
 * = 자기 사이트 멤버십만» · «탈퇴 후 복귀 = 다시 동의하면 복귀», operator removal not reopenable).
 */
@DisplayName("TASK-BE-619 — 사이트 멤버십 LEFT (본인 / 운영자) 와 동의로 복귀")
class ConsumerSiteMembershipLeaveTest {

    private static final TenantId STORE = new TenantId("ecommerce");
    private static final Instant JOINED = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant LEFT_AT = Instant.parse("2026-10-02T00:00:00Z");
    private static final Instant BACK_AT = Instant.parse("2026-10-03T00:00:00Z");

    private static ConsumerSiteMembership active() {
        return ConsumerSiteMembership.joinOnSignup("acc-1", STORE, JOINED);
    }

    @Test
    @DisplayName("본인 탈퇴 → LEFT · left_by=SELF · 시각 기록 · consented_at 보존 · 동의로 다시 열 수 있다")
    void selfLeave_isReopenable() {
        ConsumerSiteMembership left = active().leave(ConsumerSiteLeftBy.SELF, "acc-1", LEFT_AT);

        assertThat(left.getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LEFT);
        assertThat(left.getLeftBy()).isEqualTo(ConsumerSiteLeftBy.SELF);
        assertThat(left.getLeftAt()).isEqualTo(LEFT_AT);
        assertThat(left.getLeftByActorId()).isEqualTo("acc-1");
        assertThat(left.getConsentedAt()).isEqualTo(JOINED);
        assertThat(left.isReopenableByConsent()).isTrue();
    }

    @Test
    @DisplayName("운영자가 내보냄 → LEFT · left_by=OPERATOR · 동의로 다시 열 수 없다")
    void operatorLeave_isNotReopenable() {
        ConsumerSiteMembership left = active().leave(ConsumerSiteLeftBy.OPERATOR, "op-1", LEFT_AT);

        assertThat(left.getLeftBy()).isEqualTo(ConsumerSiteLeftBy.OPERATOR);
        assertThat(left.getLeftByActorId()).isEqualTo("op-1");
        assertThat(left.isReopenableByConsent()).isFalse();
        assertThatThrownBy(() -> left.rejoinOnConsent(BACK_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("본인이 떠난 뒤 운영자가 내보냄 → OPERATOR 로 다시 기록 (내보냄이 남는다)")
    void operatorAfterSelf_escalates() {
        ConsumerSiteMembership self = active().leave(ConsumerSiteLeftBy.SELF, "acc-1", LEFT_AT);

        ConsumerSiteMembership removed = self.leave(ConsumerSiteLeftBy.OPERATOR, "op-1", BACK_AT);

        assertThat(removed).isNotSameAs(self);
        assertThat(removed.getLeftBy()).isEqualTo(ConsumerSiteLeftBy.OPERATOR);
        assertThat(removed.isReopenableByConsent()).isFalse();
    }

    @Test
    @DisplayName("운영자가 내보낸 뒤 본인 탈퇴 → 그대로 (본인이 내보냄을 «복귀 가능» 으로 낮추지 못한다) · 두 번째 탈퇴도 그대로")
    void selfAfterOperator_andRepeat_areNoOps() {
        ConsumerSiteMembership removed = active().leave(ConsumerSiteLeftBy.OPERATOR, "op-1", LEFT_AT);
        ConsumerSiteMembership self = active().leave(ConsumerSiteLeftBy.SELF, "acc-1", LEFT_AT);

        assertThat(removed.leave(ConsumerSiteLeftBy.SELF, "acc-1", BACK_AT)).isSameAs(removed);
        assertThat(self.leave(ConsumerSiteLeftBy.SELF, "acc-1", BACK_AT)).isSameAs(self);
        assertThat(removed.leave(ConsumerSiteLeftBy.OPERATOR, "op-2", BACK_AT)).isSameAs(removed);
    }

    @Test
    @DisplayName("본인 탈퇴 뒤 다시 동의 → ACTIVE · consented_at = 새 동의 시각 · 탈퇴 기록 지움")
    void rejoin_afterSelfLeave() {
        ConsumerSiteMembership back = active().leave(ConsumerSiteLeftBy.SELF, "acc-1", LEFT_AT)
                .rejoinOnConsent(BACK_AT);

        assertThat(back.isActive()).isTrue();
        assertThat(back.getConsentedAt()).isEqualTo(BACK_AT);
        assertThat(back.getLeftAt()).isNull();
        assertThat(back.getLeftBy()).isNull();
        assertThat(back.getLeftByActorId()).isNull();
    }

    @Test
    @DisplayName("작성자 기록이 없는 LEFT 행(619 이전에는 없음) → 다시 열 수 없다(보수) · ACTIVE 는 탈퇴 기록을 가질 수 없다")
    void legacyLeftRow_notReopenable_andActiveCarriesNoLeaveRecord() {
        ConsumerSiteMembership legacy = ConsumerSiteMembership.reconstitute(
                "acc-1", STORE, ConsumerSiteMembershipStatus.LEFT, JOINED);

        assertThat(legacy.isReopenableByConsent()).isFalse();
        assertThat(active().isReopenableByConsent()).isFalse();
        assertThatThrownBy(() -> ConsumerSiteMembership.reconstitute("acc-1", STORE,
                ConsumerSiteMembershipStatus.ACTIVE, JOINED, LEFT_AT, ConsumerSiteLeftBy.SELF, "acc-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
