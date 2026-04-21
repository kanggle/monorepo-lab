package com.example.auth.domain.service;

/**
 * Checks whether an incoming request should be rate-limited.
 * Returns true if the request exceeds the allowed rate (i.e., should be denied).
 */
public interface RateLimiter {

    boolean isRateLimited(String clientKey, int maxRequests, long windowSeconds);
}
