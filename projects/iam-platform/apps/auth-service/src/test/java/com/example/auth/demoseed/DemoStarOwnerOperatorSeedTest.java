package com.example.auth.demoseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.password.Argon2idPasswordHasher;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * TASK-MONO-798 — the owner-only '*' SUPER_ADMIN operator has an IdP login but <b>no fixed
 * password in the repo</b> (owner decision 2026-10-17 UTC, point 2). Its credential row is
 * written at boot by {@code infra/demo/owner-operator.sh provision} with a hash computed by
 * {@code infra/demo/OwnerOperatorHash.java}. Pinned here, from the auth-service side:
 * <ul>
 *   <li>no auth-service dev seed carries a credential for that account or email — a seeded row
 *       would be exactly the fixed password the owner ruled out;</li>
 *   <li>the admin seed carries no hash literal and is SUSPENDED;</li>
 *   <li>the admin seed's {@code oidc_subject} equals {@code OWNER_ACCOUNT_ID} in the boot script —
 *       a mismatch fail-closes to a console 401 that reads like a timeout;</li>
 *   <li>the boot-time hash helper's output verifies with the login path's own hasher — a hash
 *       the login path rejects would only surface as a failed login inside a measurement window.</li>
 * </ul>
 */
@DisplayName("TASK-MONO-798 owner-only '*' operator — no fixed password")
class DemoStarOwnerOperatorSeedTest {

    private static final Path AUTH_DEV_SEEDS = Path.of("src", "main", "resources", "db", "migration-dev");
    /** Sibling module — resolved from this module's directory (Gradle test workingDir). */
    private static final Path ADMIN_SEED = Path.of("..", "admin-service", "src", "main", "resources", "db",
            "migration-dev", "R__seed_demo_star_owner_operator.sql");

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle"))) {
            p = p.getParent();
        }
        assertThat(p).as("repo root (settings.gradle) above the module dir").isNotNull();
        return p;
    }

    private static String read(Path p) throws IOException {
        assertThat(p).as("%s must exist", p).exists();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String statementsOnly(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    private static String scriptConstant(String name) throws IOException {
        Matcher m = Pattern.compile("(?m)^" + name + "=\"([^\"]+)\"")
                .matcher(read(repoRoot().resolve("infra/demo/owner-operator.sh")));
        assertThat(m.find()).as("%s= in infra/demo/owner-operator.sh", name).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("no auth-service dev seed names the owner account or its email")
    void noCredentialSeedForTheOwnerAccount() throws IOException {
        String accountId = scriptConstant("OWNER_ACCOUNT_ID");
        String email = scriptConstant("OWNER_EMAIL");
        List<Path> seeds;
        try (Stream<Path> s = Files.list(AUTH_DEV_SEEDS)) {
            seeds = s.filter(p -> p.toString().endsWith(".sql")).toList();
        }
        // Control: the population is the directory the sibling credential seeds live in.
        assertThat(seeds).anyMatch(p -> p.getFileName().toString()
                .equals("R__seed_demo_platform_operator_credential.sql"));
        for (Path seed : seeds) {
            String body = read(seed);
            assertThat(body).as("%s must not seed the owner credential (a fixed password)", seed)
                    .doesNotContain(accountId).doesNotContain(email);
        }
    }

    @Test
    @DisplayName("the admin seed is SUSPENDED, has no hash literal, and its oidc_subject equals the boot script's account id")
    void adminSeedIsSuspendedHashlessAndLinked() throws IOException {
        String statements = statementsOnly(read(ADMIN_SEED));
        assertThat(statements).doesNotContain("$argon2");
        assertThat(statements).contains("'SUSPENDED'").doesNotContain("'ACTIVE'");
        assertThat(statements)
                .as("the operator row must carry the account id the boot step writes into credentials")
                .contains("'" + scriptConstant("OWNER_ACCOUNT_ID") + "'")
                .contains("'" + scriptConstant("OWNER_EMAIL") + "'")
                .contains("'" + scriptConstant("OWNER_OPERATOR_ID") + "', '*'");
    }

    @Test
    @DisplayName("the boot-time hash helper's output verifies with the login path's hasher; short input refused")
    void bootHashHelperOutputVerifies(@TempDir Path out) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("the test JVM must be a JDK (the demo host's is: openjdk-21-jdk-headless)").isNotNull();
        Path source = repoRoot().resolve("infra/demo/OwnerOperatorHash.java");
        int rc = compiler.run(null, null, null,
                "-d", out.toString(), "-classpath", System.getProperty("java.class.path"), source.toString());
        assertThat(rc).as("javac %s", source).isZero();

        try (URLClassLoader loader = new URLClassLoader(new URL[] {out.toUri().toURL()},
                DemoStarOwnerOperatorSeedTest.class.getClassLoader())) {
            Method hash = loader.loadClass("OwnerOperatorHash").getMethod("hash", String.class);
            String password = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8S9t0";
            String hashed = (String) hash.invoke(null, password);

            assertThat(hashed).startsWith("$argon2id$").doesNotContain(password);
            Argon2idPasswordHasher loginHasher = new Argon2idPasswordHasher();
            assertThat(loginHasher.verify(password, hashed)).isTrue();
            // Control: the predicate can say no.
            assertThat(loginHasher.verify(password + "x", hashed)).isFalse();

            try {
                hash.invoke(null, "Qz7tiny");
                throw new AssertionError("a 7-character password was accepted");
            } catch (InvocationTargetException e) {
                // The message names the rule, never the value.
                assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageNotContaining("Qz7tiny");
            }
        }
    }
}
