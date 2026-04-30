package com.example.auth.domain.repository;

/**
 * Port interface for refresh token blacklist (backed by Redis).
 */
public interface TokenBlacklist {

    /**
     * Adds a refresh token JTI to the blacklist.
     *
     * @param jti            the JWT ID of the refresh token
     * @param ttlSeconds     TTL matching the token's remaining lifetime
     */
    void blacklist(String jti, long ttlSeconds);

    /**
     * Checks if a refresh token JTI is blacklisted.
     * Returns true if blacklisted OR if Redis is unavailable (fail-closed).
     */
    boolean isBlacklisted(String jti);
}
