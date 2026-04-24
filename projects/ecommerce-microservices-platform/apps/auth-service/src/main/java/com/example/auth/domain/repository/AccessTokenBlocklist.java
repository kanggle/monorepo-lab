package com.example.auth.domain.repository;

import java.util.UUID;

/**
 * Stores invalidated access tokens (e.g., after logout) until they expire.
 * Prevents reuse of access tokens after the user has explicitly logged out.
 */
public interface AccessTokenBlocklist {

    void block(String token, long ttlSeconds);

    boolean isBlocked(String token);

    void blockByUserId(UUID userId, long ttlSeconds);

    boolean isUserBlocked(UUID userId);
}
