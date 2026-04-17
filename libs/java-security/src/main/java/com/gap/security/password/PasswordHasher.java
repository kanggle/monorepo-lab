package com.gap.security.password;

/**
 * Hashes and verifies passwords.
 * <p>
 * Implementations must be thread-safe.
 */
public interface PasswordHasher {

    /**
     * Hashes the given plain-text password.
     *
     * @param plainPassword the plain-text password (never null or empty)
     * @return the encoded hash string (algorithm-specific format)
     */
    String hash(String plainPassword);

    /**
     * Verifies a plain-text password against a previously hashed value.
     *
     * @param plainPassword the plain-text password to check
     * @param hashedPassword the stored hash to verify against
     * @return true if the password matches the hash
     */
    boolean verify(String plainPassword, String hashedPassword);
}
