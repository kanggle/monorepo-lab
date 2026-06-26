package com.example.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StrandedRefund 도메인 모델 단위 테스트 (TASK-BE-438)")
class StrandedRefundTest {

    private static final Instant T0 = Instant.parse("2026-06-26T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-06-26T10:00:05Z");

    @Test
    @DisplayName("open(...) 은 STRANDED, attempts=0, next_attempt_at=now 인 행을 만든다")
    void open_createsStrandedRow() {
        StrandedRefund r = StrandedRefund.open("pay-1", "order-1", "pk_1", 30000L, "reason", T0);

        assertThat(r.getStatus()).isEqualTo(StrandedRefundStatus.STRANDED);
        assertThat(r.getAttempts()).isZero();
        assertThat(r.getNextAttemptAt()).isEqualTo(T0);
        assertThat(r.getCreatedAt()).isEqualTo(T0);
        assertThat(r.isOpen()).isTrue();
    }

    @Test
    @DisplayName("markResolved 는 RESOLVED + resolvedAt 을 세팅하며, 재호출은 idempotent no-op 이다")
    void markResolved_setsResolvedAndIsIdempotent() {
        StrandedRefund r = StrandedRefund.open("pay-1", "order-1", "pk_1", 30000L, "reason", T0);

        r.markResolved(T1);
        assertThat(r.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
        assertThat(r.getResolvedAt()).isEqualTo(T1);

        r.markResolved(T1); // idempotent
        assertThat(r.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
    }

    @Test
    @DisplayName("recordTransientFailure 는 attempts 증가 + next_attempt_at/last_error 갱신, STRANDED 유지")
    void recordTransientFailure_incrementsAndStaysStranded() {
        StrandedRefund r = StrandedRefund.open("pay-1", "order-1", "pk_1", 30000L, "reason", T0);

        r.recordTransientFailure(T1, T1.plusSeconds(2), "boom");

        assertThat(r.getStatus()).isEqualTo(StrandedRefundStatus.STRANDED);
        assertThat(r.getAttempts()).isEqualTo(1);
        assertThat(r.getNextAttemptAt()).isEqualTo(T1.plusSeconds(2));
        assertThat(r.getLastError()).isEqualTo("boom");
    }

    @Test
    @DisplayName("markUnresolved 는 UNRESOLVED 로 전이하고 attempts 를 증가시킨다 (resolvedAt 은 null 유지)")
    void markUnresolved_terminates() {
        StrandedRefund r = StrandedRefund.open("pay-1", "order-1", "pk_1", 30000L, "reason", T0);

        r.markUnresolved(T1, "cap exhausted");

        assertThat(r.getStatus()).isEqualTo(StrandedRefundStatus.UNRESOLVED);
        assertThat(r.getAttempts()).isEqualTo(1);
        assertThat(r.getResolvedAt()).isNull();
        assertThat(r.getLastError()).isEqualTo("cap exhausted");
    }

    @Test
    @DisplayName("wouldExhaust(cap) 은 attempts+1 이 cap 이상일 때 true")
    void wouldExhaust_atCap() {
        StrandedRefund r = StrandedRefund.reconstitute(
                1L, "pay-1", "order-1", "pk_1", 30000L, "reason",
                StrandedRefundStatus.STRANDED, 2, T0, null, T0, T0, null);

        assertThat(r.wouldExhaust(3)).isTrue();
        assertThat(r.wouldExhaust(4)).isFalse();
    }

    @Test
    @DisplayName("terminal 행에 대한 transition 은 IllegalStateException — terminal 은 terminal")
    void transitionOnTerminal_throws() {
        StrandedRefund r = StrandedRefund.open("pay-1", "order-1", "pk_1", 30000L, "reason", T0);
        r.markUnresolved(T1, "cap");

        assertThatThrownBy(() -> r.recordTransientFailure(T1, T1, "x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> r.markResolved(T1))
                .isInstanceOf(IllegalStateException.class);
    }
}
