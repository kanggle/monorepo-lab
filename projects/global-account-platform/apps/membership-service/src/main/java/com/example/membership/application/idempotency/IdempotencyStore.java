package com.example.membership.application.idempotency;

import java.util.Optional;

/**
 * Port: stores idempotency-key → subscriptionId mappings with TTL.
 */
public interface IdempotencyStore {

    /**
     * Atomically stores the key if absent. Returns true if stored, false if a value already existed.
     */
    boolean putIfAbsent(String key, String subscriptionId);

    Optional<String> get(String key);
}
