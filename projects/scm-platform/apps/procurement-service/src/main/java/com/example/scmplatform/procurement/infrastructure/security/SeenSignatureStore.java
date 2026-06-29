package com.example.scmplatform.procurement.infrastructure.security;

import java.time.Duration;

/**
 * Short-TTL store of already-seen webhook signatures, used for replay
 * rejection. The signature itself is the replay nonce.
 */
public interface SeenSignatureStore {

    /**
     * Atomically records {@code signatureHex} if it has not been seen.
     *
     * @param signatureHex the lowercase-hex webhook signature (the replay nonce)
     * @param ttl          how long the nonce is remembered (must be ≥ the
     *                     freshness window so a replay cannot outlive memory)
     * @return {@code true} if the signature was newly recorded (fresh);
     *         {@code false} if it was already present (replay)
     */
    boolean markIfFresh(String signatureHex, Duration ttl);
}
