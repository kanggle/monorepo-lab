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
     * Used to populate the {@code auth.session.revoked} event's {@code revokedJtis} field when
     * performing a bulk revoke.
     */
    List<String> findActiveJtisByAccountId(String accountId);

    /**
     * Finds every child token that was rotated from the given JTI.
     *
     * <p>TASK-BE-606: a list, not an {@code Optional}. {@code rotated_from} carries a
     * NON-unique index ({@code V0001}), and two concurrent refreshes of the same token that
     * both read before either commits each write a child with the same {@code rotated_from}.
     * The former {@code Optional} finder threw {@code IncorrectResultSizeDataAccessException}
     * on exactly that state — on the very lookup the reuse handler runs.
     *
     * @return the children, empty when the token was never rotated
     */
    List<RefreshToken> findAllByRotatedFrom(String jti);

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
