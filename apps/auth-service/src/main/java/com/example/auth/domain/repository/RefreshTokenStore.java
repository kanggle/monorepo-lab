package com.example.auth.domain.repository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Abstraction for storing and retrieving refresh tokens.
 * Implementation uses Redis with TTL-based expiry.
 */
public interface RefreshTokenStore {

    void save(String token, UUID userId, long ttlSeconds);

    Optional<UUID> findUserIdByToken(String token);

    boolean isRevoked(String token);

    /**
     * 토큰을 무효화하고 revoked 상태로 표시한다.
     * @return 실제로 삭제된 경우 true, 이미 존재하지 않아 삭제되지 않은 경우 false
     */
    boolean invalidate(String token, long revokedTtlSeconds);

    /**
     * 해당 사용자의 모든 활성 refresh token 해시를 반환한다.
     */
    Set<String> findAllTokenHashesByUserId(UUID userId);

    /**
     * 해당 사용자의 모든 refresh token을 무효화한다.
     */
    void invalidateAllByUserId(UUID userId, long revokedTtlSeconds);
}
