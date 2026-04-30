package com.example.auth.domain.repository;

/**
 * Port interface for login failure counting (backed by Redis).
 */
public interface LoginAttemptCounter {

    /**
     * Returns the current failure count for the given email hash.
     * Returns 0 if Redis is unavailable (fail-open for counter reads).
     */
    int getFailureCount(String emailHash);

    /**
     * Increments the failure count and resets the TTL window.
     */
    void incrementFailureCount(String emailHash);

    /**
     * Clears the failure count on successful login.
     */
    void resetFailureCount(String emailHash);
}
