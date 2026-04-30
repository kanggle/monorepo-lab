package com.example.auth.domain.token;

import com.example.auth.domain.repository.RefreshTokenRepository;

import java.util.Objects;

/**
 * Pure domain service that detects refresh-token reuse.
 *
 * <p>A refresh token is considered "reused" when it has already been rotated into a child token
 * (i.e., another refresh token exists whose {@code rotated_from} points at the submitted token's
 * JTI) and is presented again for rotation. This indicates either a bug in a client, a stolen
 * token, or a leaked token being replayed by an attacker.
 *
 * <p>This class has no framework dependencies and only consults the repository port.
 */
public class TokenReuseDetector {

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenReuseDetector(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository,
                "refreshTokenRepository must not be null");
    }

    /**
     * Checks whether the given refresh token has already been rotated and is therefore being
     * reused. An already-revoked token whose child also exists is still classified as reuse,
     * since a child token proves rotation has occurred.
     *
     * @param submittedToken the refresh token that was presented for rotation
     * @return {@code true} if a child token rotated from this token's JTI exists
     */
    public boolean isReuse(RefreshToken submittedToken) {
        Objects.requireNonNull(submittedToken, "submittedToken must not be null");
        return refreshTokenRepository.existsByRotatedFrom(submittedToken.getJti());
    }
}
