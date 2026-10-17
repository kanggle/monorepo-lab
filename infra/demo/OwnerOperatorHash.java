// =============================================================================
// infra/demo/OwnerOperatorHash.java — TASK-MONO-798
// =============================================================================
// Reads ONE password from stdin and prints its Argon2id hash on stdout, computed
// by the login path's own hasher (`com.example.security.password.Argon2idPasswordHasher`,
// default parameters — the same instance auth-service builds in JwtConfig).
//
// Run by `infra/demo/owner-operator.sh provision` on the demo host with the host JDK's
// single-file source launcher, against the jars taken out of the baked auth-service boot
// jar (password4j + its slf4j-api + java-security):
//
//   printf '%s' "$pw" | java -cp "<dir>/*" infra/demo/OwnerOperatorHash.java
//
// 🔴 The password arrives on stdin, never argv (argv is readable in `ps` by every local
//    user). It is never printed — only the hash is, and only after the hasher has verified
//    its own output (a hash the login path cannot verify would be a credential nobody can
//    use, discovered only as a failed login inside a measurement window).
// 🔵 auth-service `DemoStarOwnerOperatorSeedTest` compiles and runs this file against the
//    test classpath and verifies the result with a fresh hasher instance.
// =============================================================================

import com.example.security.password.Argon2idPasswordHasher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class OwnerOperatorHash {

    /** The shortest password this tool accepts — the generator in owner-operator.sh makes 40. */
    static final int MIN_LENGTH = 24;

    private OwnerOperatorHash() {
    }

    /** Hashes {@code password} with the login path's hasher and re-verifies the result. */
    public static String hash(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("password shorter than " + MIN_LENGTH + " characters");
        }
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String hashed = hasher.hash(password);
        if (!hasher.verify(password, hashed)) {
            throw new IllegalStateException("the hasher could not verify its own output");
        }
        return hashed;
    }

    public static void main(String[] args) throws IOException {
        String password = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        try {
            System.out.print(hash(password));
        } catch (RuntimeException e) {
            // The message names the rule, never the value.
            System.err.println("[owner-operator] hash failed: " + e.getMessage());
            System.exit(2);
        }
    }
}
