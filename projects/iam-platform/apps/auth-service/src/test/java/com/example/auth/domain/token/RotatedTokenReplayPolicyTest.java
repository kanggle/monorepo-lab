package com.example.auth.domain.token;

import com.example.auth.domain.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-606 — {@link RotatedTokenReplayPolicy}: the grace window (owner decision
 * 2026-09-26, option B, N = 30 s) and what falls outside it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RotatedTokenReplayPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-26T03:00:00Z");
    private static final String PARENT = "parent-token-Abc";

    @Mock
    private RefreshTokenRepository repository;

    private RotatedTokenReplayPolicy policy() {
        return new RotatedTokenReplayPolicy(repository, Duration.ofSeconds(30));
    }

    private static RefreshToken child(String jti, String rotatedFrom, Instant issuedAt) {
        return RefreshToken.create(jti, "acc-1", "fan-platform", issuedAt,
                issuedAt.plusSeconds(3600), rotatedFrom, null, null);
    }

    @Test
    @DisplayName("자식 없음 → NOT_ROTATED")
    void noChild_notRotated() {
        when(repository.findAllByRotatedFrom(PARENT)).thenReturn(List.of());

        assertThat(policy().assess(PARENT, NOW).verdict())
                .isEqualTo(RotatedTokenReplayPolicy.Verdict.NOT_ROTATED);
    }

    @Test
    @DisplayName("자식 1 · 체인 머리 · 정확히 30s 전 → WITHIN_GRACE (경계 포함)")
    void oneHeadChild_exactlyAtGraceBoundary_withinGrace() {
        RefreshToken c = child("c1", PARENT, NOW.minusSeconds(30));
        when(repository.findAllByRotatedFrom(PARENT)).thenReturn(List.of(c));
        when(repository.existsByRotatedFrom("c1")).thenReturn(false);

        assertThat(policy().assess(PARENT, NOW).verdict())
                .isEqualTo(RotatedTokenReplayPolicy.Verdict.WITHIN_GRACE);
    }

    @Test
    @DisplayName("자식 1 · 31s 전 → REUSE (머리 여부는 묻지도 않는다)")
    void oneChild_pastGrace_reuse() {
        when(repository.findAllByRotatedFrom(PARENT)).thenReturn(List.of(child("c1", PARENT, NOW.minusSeconds(31))));

        assertThat(policy().assess(PARENT, NOW).verdict())
                .isEqualTo(RotatedTokenReplayPolicy.Verdict.REUSE);
        verify(repository, never()).existsByRotatedFrom(any());
    }

    @Test
    @DisplayName("자식 1 · 5s 전이지만 이미 다시 회전됨 → REUSE")
    void oneChild_alreadyRotatedFurther_reuse() {
        when(repository.findAllByRotatedFrom(PARENT)).thenReturn(List.of(child("c1", PARENT, NOW.minusSeconds(5))));
        when(repository.existsByRotatedFrom("c1")).thenReturn(true);

        assertThat(policy().assess(PARENT, NOW).verdict())
                .isEqualTo(RotatedTokenReplayPolicy.Verdict.REUSE);
    }

    @Test
    @DisplayName("자식 2 · 둘 다 유예 안 → REUSE, 가장 이른 자식이 originalRotation")
    void twoChildren_reuse_earliestIsOriginal() {
        RefreshToken later = child("c2", PARENT, NOW.minusSeconds(2));
        RefreshToken earlier = child("c1", PARENT, NOW.minusSeconds(3));
        when(repository.findAllByRotatedFrom(PARENT)).thenReturn(List.of(later, earlier));

        RotatedTokenReplayPolicy.Assessment a = policy().assess(PARENT, NOW);

        assertThat(a.verdict()).isEqualTo(RotatedTokenReplayPolicy.Verdict.REUSE);
        assertThat(a.children()).hasSize(2);
        assertThat(a.originalRotation().getJti()).isEqualTo("c1");
    }

    @Test
    @DisplayName("대소문자만 다른 토큰의 자식(ci 콜레이션이 돌려준 행) → 이 토큰의 자식이 아니다: NOT_ROTATED")
    void caseInsensitiveCollationMatch_isNotThisTokensChild() {
        when(repository.findAllByRotatedFrom(PARENT))
                .thenReturn(List.of(child("c1", PARENT.toLowerCase(), NOW.minusSeconds(600))));

        assertThat(policy().assess(PARENT, NOW).verdict())
                .isEqualTo(RotatedTokenReplayPolicy.Verdict.NOT_ROTATED);
    }

    @Test
    @DisplayName("음수 유예 → 생성 거부")
    void negativeGrace_rejected() {
        assertThatThrownBy(() -> new RotatedTokenReplayPolicy(repository, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
