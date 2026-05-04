package com.example.security.password;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;

import java.util.Objects;

/**
 * Argon2id implementation of {@link PasswordHasher} using password4j (pure Java).
 * <p>
 * Default parameters follow the spec: memory=65536KB, iterations=3, parallelism=1,
 * output length=32 bytes, salt length=16 bytes.
 * <p>
 * Thread-safe: the Argon2Function instance is immutable after construction.
 */
public final class Argon2idPasswordHasher implements PasswordHasher {

    private static final int DEFAULT_MEMORY_KB = 65536;
    private static final int DEFAULT_ITERATIONS = 3;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int DEFAULT_OUTPUT_LENGTH = 32;
    private static final int DEFAULT_SALT_LENGTH = 16;

    private final Argon2Function argon2Function;

    /**
     * Creates an Argon2id hasher with the given parameters.
     *
     * @param memoryKb     memory cost in KB
     * @param iterations   time cost (number of iterations)
     * @param parallelism  parallelism factor
     * @param outputLength hash output length in bytes
     * @param saltLength   salt length in bytes
     */
    public Argon2idPasswordHasher(int memoryKb, int iterations, int parallelism,
                                   int outputLength, int saltLength) {
        if (saltLength <= 0) {
            throw new IllegalArgumentException("saltLength must be positive");
        }
        this.argon2Function = Argon2Function.getInstance(
                memoryKb, iterations, parallelism, outputLength, Argon2.ID, saltLength);
    }

    /**
     * Creates an Argon2id hasher with spec-default parameters:
     * memory=65536, iterations=3, parallelism=1, outputLength=32, saltLength=16.
     */
    public Argon2idPasswordHasher() {
        this(DEFAULT_MEMORY_KB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM,
                DEFAULT_OUTPUT_LENGTH, DEFAULT_SALT_LENGTH);
    }

    @Override
    public String hash(String plainPassword) {
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");
        if (plainPassword.isEmpty()) {
            throw new IllegalArgumentException("plainPassword must not be empty");
        }

        return Password.hash(plainPassword)
                .with(argon2Function)
                .getResult();
    }

    @Override
    public boolean verify(String plainPassword, String hashedPassword) {
        Objects.requireNonNull(plainPassword, "plainPassword must not be null");
        Objects.requireNonNull(hashedPassword, "hashedPassword must not be null");

        return Password.check(plainPassword, hashedPassword)
                .with(argon2Function);
    }
}
