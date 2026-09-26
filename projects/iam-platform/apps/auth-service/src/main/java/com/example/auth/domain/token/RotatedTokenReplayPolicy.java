package com.example.auth.domain.token;

import com.example.auth.domain.repository.RefreshTokenRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * TASK-BE-606 — classifies a refresh token that is presented again after it was rotated.
 *
 * <p><b>Why the mirror store and not the SAS authorization.</b> Spring Authorization Server
 * keeps only the CURRENT refresh token on an authorization, so a token that was rotated away
 * is simply unknown to it. The {@code refresh_tokens} mirror store remembers the chain: every
 * rotation writes a child row whose {@code rotated_from} is the parent token. "Some row was
 * rotated from the submitted token" is therefore the evidence that the token has been used
 * before — and it does not depend on the submitted token's OWN row existing (its
 * initial-issuance INSERT is swallowed on failure by {@code DomainSyncOAuth2AuthorizationService}).
 *
 * <p><b>Grace window (owner decision 2026-09-26, option B, N = 30 s).</b> A second request with
 * the same token that arrives right after the first one rotated it is, far more often than
 * theft, a client race — two console tabs refreshing at once, or a retry after a lost
 * response. Such a replay is {@link Verdict#WITHIN_GRACE} when ALL of these hold:
 * <ol>
 *   <li>exactly one child exists (two children = the chain already forked, see below);</li>
 *   <li>that child is still the head of the chain (nothing was rotated from it yet) — once the
 *       winner has itself refreshed again, the old token has no benign reason to reappear;</li>
 *   <li>the child was issued at most {@code grace} before {@code now}.</li>
 * </ol>
 * Anything else with a child is {@link Verdict#REUSE}. The grace verdict is answered with
 * {@code invalid_grant} and nothing is revoked, so the winner's session survives.
 *
 * <p>Two children means two concurrent refreshes both got through before either committed;
 * the chain now has two live branches and cannot be proven to belong to one holder, so it is
 * REUSE even inside the window.
 *
 * <p>Pure domain logic: only the repository port and {@code java.time}.
 */
public class RotatedTokenReplayPolicy {

    /** How a presented token relates to the rotation chain. */
    public enum Verdict {
        /** No row was rotated from the token — it has not been used before. */
        NOT_ROTATED,
        /** Rotated moments ago into a child that is still the chain head — a client race. */
        WITHIN_GRACE,
        /** Rotated before and presented again outside the grace conditions — theft signal. */
        REUSE
    }

    /**
     * The verdict and the children it was drawn from.
     *
     * @param verdict  the classification
     * @param children every row rotated from the presented token, earliest first
     */
    public record Assessment(Verdict verdict, List<RefreshToken> children) {

        public Assessment {
            children = List.copyOf(children);
        }

        /** The earliest child = the original rotation; {@code null} when not rotated. */
        public RefreshToken originalRotation() {
            return children.isEmpty() ? null : children.get(0);
        }
    }

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration grace;

    public RotatedTokenReplayPolicy(RefreshTokenRepository refreshTokenRepository, Duration grace) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository,
                "refreshTokenRepository must not be null");
        this.grace = Objects.requireNonNull(grace, "grace must not be null");
        if (grace.isNegative()) {
            throw new IllegalArgumentException("grace must not be negative, got " + grace);
        }
    }

    public Duration grace() {
        return grace;
    }

    /**
     * @param presentedToken the refresh token value (the mirror row's {@code jti})
     * @param now            the time of the request
     */
    public Assessment assess(String presentedToken, Instant now) {
        Objects.requireNonNull(presentedToken, "presentedToken must not be null");
        Objects.requireNonNull(now, "now must not be null");

        List<RefreshToken> children = refreshTokenRepository.findAllByRotatedFrom(presentedToken).stream()
                // The column's collation (utf8mb4_unicode_ci) compares case-INsensitively, so the
                // query can also return a child of a token that differs only in letter case.
                // Token values are case-sensitive: keep exact matches only.
                .filter(child -> presentedToken.equals(child.getRotatedFrom()))
                .sorted(Comparator.comparing(RefreshToken::getIssuedAt))
                .toList();
        if (children.isEmpty()) {
            return new Assessment(Verdict.NOT_ROTATED, children);
        }
        if (children.size() == 1 && isWithinGrace(children.get(0), now)) {
            return new Assessment(Verdict.WITHIN_GRACE, children);
        }
        return new Assessment(Verdict.REUSE, children);
    }

    private boolean isWithinGrace(RefreshToken onlyChild, Instant now) {
        // issued_at is non-null by construction (RefreshToken) — measured from the rotation.
        if (now.isAfter(onlyChild.getIssuedAt().plus(grace))) {
            return false;
        }
        // The child must still be the head: once it was itself rotated, the old token has no
        // race left to be part of.
        return !refreshTokenRepository.existsByRotatedFrom(onlyChild.getJti());
    }
}
