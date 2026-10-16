package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-795 (ADR-MONO-083 P8 · Failure 2, owner decision Q3) — no ecommerce service may parse the gateway's
 * {@code X-User-Role} header itself. That header is the comma-joined {@code roles} claim this gateway injects
 * ({@code GatewayIdentityConfig}); before this ticket eight services each carried their own split-trim-compare copy,
 * and one of them compared case-sensitively while the other seven ignored case — the «one copy fixed, its sibling
 * not» failure in the flesh. The comparison rule now lives once, in {@code libs/java-web}
 * {@code com.example.web.security.RoleListHeader}; every service asks it.
 *
 * <p>This lives in the gateway module because the gateway owns the header's shape. It walks
 * {@code projects/ecommerce-microservices-platform/apps/*&#47;src/main/java} (the test's working directory is this
 * module, so the apps root is {@code ..}).
 *
 * <p>Predicate: a role-named variable split on a comma — {@code userRole.split(",")}, {@code roles.split(",")}. The
 * legitimate comma splits in these services (an {@code X-Forwarded-For} list, a sort spec, a client-id allow-list)
 * do not name a role, so they are not caught; {@link #predicate_bites} proves both directions.
 */
class RoleHeaderCopyGuardTest {

    static final Pattern ROLE_SPLIT = Pattern.compile("\\b\\w*[Rr]ole\\w*\\s*\\.\\s*split\\s*\\(");

    private static final Path APPS = Path.of("..");

    @Test
    @DisplayName("🔴 predicate bites a role-header split and spares the legitimate comma splits")
    void predicate_bites() {
        assertThat(ROLE_SPLIT.matcher("for (String role : userRole.split(\",\")) {").find()).isTrue();
        assertThat(ROLE_SPLIT.matcher("String[] r = roles.split(\",\");").find()).isTrue();
        assertThat(ROLE_SPLIT.matcher("return xff.split(\",\")[0].trim();").find()).isFalse();
        assertThat(ROLE_SPLIT.matcher("String[] parts = sort.split(\",\");").find()).isFalse();
        assertThat(ROLE_SPLIT.matcher("Arrays.stream(allowedClientIds.split(\",\"))").find()).isFalse();
    }

    @Test
    @DisplayName("🔴 no ecommerce service splits a role header itself — ask libs/java-web RoleListHeader")
    void noServiceCopiesTheRoleHeaderParse() throws IOException {
        assertThat(APPS.resolve("gateway-service").resolve("build.gradle"))
                .as("population control — the test runs from the gateway module, so .. is the apps root").exists();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> apps = Files.list(APPS)) {
            for (Path app : apps.toList()) {
                Path main = app.resolve("src").resolve("main").resolve("java");
                if (Files.isDirectory(main)) {
                    try (Stream<Path> files = Files.walk(main)) {
                        files.filter(f -> f.toString().endsWith(".java")).forEach(sources::add);
                    }
                }
            }
        }
        assertThat(sources).as("population control — the ecommerce services' main sources were found").hasSizeGreaterThan(100);

        List<String> copies = new ArrayList<>();
        for (Path f : sources) {
            List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (ROLE_SPLIT.matcher(lines.get(i)).find()) {
                    copies.add(APPS.relativize(f) + ":" + (i + 1) + "  " + lines.get(i).trim());
                }
            }
        }
        assertThat(copies)
                .as("a service parses the role header itself — use com.example.web.security.RoleListHeader "
                        + "(libs/java-web) so the comparison rule cannot drift between copies (TASK-MONO-795)")
                .isEmpty();
    }
}
