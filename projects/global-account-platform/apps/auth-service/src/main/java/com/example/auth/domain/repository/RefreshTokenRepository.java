package com.example.auth.domain.repository;

import com.example.auth.domain.token.RefreshToken;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for refresh token persistence.
 */
public interface RefreshTokenRepository {

    Optional<RefreshToken> findByJti(String jti);

    RefreshToken save(RefreshToken refreshToken);

    boolean existsByRotatedFrom(String jti);

    /**
     * Revokes all active refresh tokens for the given account.
     * @return the number of tokens revoked
     */
    int revokeAllByAccountId(String accountId);

    /**
     * Returns the jtis of all currently-active (non-revoked) refresh tokens for the account.
     * Used to populate the {@code session.revoked} event's {@code revokedJtis} field when
     * performing a bulk revoke.
     */
    List<String> findActiveJtisByAccountId(String accountId);

    /**
     * Finds the child token that was rotated from the given JTI.
     */
    java.util.Optional<RefreshToken> findByRotatedFrom(String jti);

    /**
     * Returns the jtis of all currently-active (non-revoked) refresh tokens for the device.
     * Used to populate the {@code auth.session.revoked} event's {@code revokedJtis} field.
     */
    List<String> findActiveJtisByDeviceId(String deviceId);

    /**
     * Revokes all active refresh tokens for the given device.
     * @return the number of tokens revoked
     */
    int revokeAllByDeviceId(String deviceId);
}
