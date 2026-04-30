package com.gap.security.jwt;

import java.security.PublicKey;
import java.util.Optional;

/**
 * Provides public keys for JWT verification, typically sourced from a JWKS endpoint.
 * <p>
 * Implementation is the responsibility of each service's infrastructure layer.
 */
public interface JwksProvider {

    /**
     * Returns the public key matching the given key ID (kid).
     *
     * @param kid the key ID from the JWT header
     * @return the matching public key, or empty if no key matches
     */
    Optional<PublicKey> getPublicKey(String kid);
}
