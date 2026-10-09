package com.example.admin.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-771 S4 — owner decision OD-5 pinned statically: the SUPER_ADMIN {@code require_2fa} relaxation
 * reaches the portfolio demo ONLY.
 *
 * <ul>
 *   <li>The relaxing statement lives only in {@code db/migration-demo}; neither {@code db/migration} (prod)
 *       nor {@code db/migration-dev} (default profile = every developer's local DB, and the CI e2e profile)
 *       sets SUPER_ADMIN's flag to FALSE — so V0013's TRUE stands everywhere but the demo.</li>
 *   <li>No profile yml of this service (default · dev · e2e · prod · test) names {@code db/migration-demo};
 *       prod stays pinned to {@code classpath:db/migration} alone.</li>
 *   <li>The only repo file that adds the location is the demo-only overlay
 *       {@code infra/demo/iam-traefik.override.yml}; the CI harness compose does not.</li>
 * </ul>
 * A relaxation that leaks to the default profile would silently turn off the S4 role term for every
 * developer and CI e2e run — exactly what OD-5 did not decide.
 */
@DisplayName("OD-5: SUPER_ADMIN require_2fa relaxation is demo-only (TASK-MONO-771 S4)")
class DemoOnlyRequire2faRelaxationTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Pattern RELAX_SUPER_ADMIN = Pattern.compile(
            "(?is)UPDATE\\s+admin_roles\\s+SET\\s+require_2fa\\s*=\\s*(FALSE|0)\\s+WHERE\\s+name\\s*=\\s*'SUPER_ADMIN'");
    private static final String DEMO_LOCATION = "db/migration-demo";

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(p.toString(), e);
        }
    }

    private static List<Path> sqlUnder(String dir) throws IOException {
        try (Stream<Path> s = Files.list(RESOURCES.resolve(dir))) {
            return s.filter(p -> p.toString().endsWith(".sql")).toList();
        }
    }

    private static Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle"))) {
            p = p.getParent();
        }
        assertThat(p).as("repo root (settings.gradle) above the module dir").isNotNull();
        return p;
    }

    @Test
    @DisplayName("V0013 sets SUPER_ADMIN require_2fa TRUE; nothing in db/migration or db/migration-dev relaxes it")
    void prodAndDefaultProfileKeepRequire2faTrue() throws IOException {
        assertThat(read(RESOURCES.resolve("db/migration/V0013__operator_totp_and_require_2fa.sql")))
                .containsPattern("(?is)require_2fa\\s*=\\s*TRUE.*SUPER_ADMIN");
        for (String dir : List.of("db/migration", "db/migration-dev")) {
            for (Path sql : sqlUnder(dir)) {
                assertThat(RELAX_SUPER_ADMIN.matcher(read(sql)).find())
                        .as("%s must not relax SUPER_ADMIN require_2fa (OD-5 = demo only)", sql)
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("the relaxation exists, and only in db/migration-demo")
    void relaxationLivesInDemoLocation() throws IOException {
        List<Path> demo = sqlUnder(DEMO_LOCATION);
        assertThat(demo).anyMatch(p -> RELAX_SUPER_ADMIN.matcher(read(p)).find());
    }

    @Test
    @DisplayName("no profile yml loads db/migration-demo; prod is db/migration only")
    void noProfileLoadsDemoLocation() throws IOException {
        List<Path> ymls = List.of(
                RESOURCES.resolve("application.yml"),
                RESOURCES.resolve("application-dev.yml"),
                RESOURCES.resolve("application-e2e.yml"),
                RESOURCES.resolve("application-prod.yml"),
                Path.of("src", "test", "resources", "application-test.yml"));
        for (Path y : ymls) {
            assertThat(read(y)).as("%s must not load %s", y, DEMO_LOCATION).doesNotContain(DEMO_LOCATION);
        }
        assertThat(read(RESOURCES.resolve("application-prod.yml")))
                .containsPattern("locations:\\s*classpath:db/migration\\s*\\n");
    }

    @Test
    @DisplayName("only the demo overlay adds the location; the CI harness compose does not")
    void onlyDemoOverlayAddsLocation() {
        Path root = repoRoot();
        assertThat(read(root.resolve("infra/demo/iam-traefik.override.yml"))).contains(DEMO_LOCATION);
        assertThat(read(root.resolve("projects/iam-platform/docker-compose.e2e.yml"))).doesNotContain(DEMO_LOCATION);
    }
}
