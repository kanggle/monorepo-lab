package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) — {@link WorkloadTenantCatalog}.
 *
 * <p>The catalog is a hand-kept table, and this repo's recurring failure is exactly that: a
 * hand-kept declaration nothing compares against the machine truth (TASK-MONO-345 service map,
 * -352 error registry, -363 ADR index, -371 JWT claims). So the first cell compares the key set
 * against the Flyway migrations, in both directions.
 *
 * <p><b>The population is defined, not assumed.</b> This table governs clients that can reach
 * the assume-tenant exchange at all — i.e. clients the migrations grant
 * {@code urn:ietf:params:oauth:grant-type:token-exchange}. A client without that grant never
 * reaches the provider branch, so enumerating it would be enumerating a population this table
 * does not govern.
 *
 * <p>🔴 <b>No floor on the size.</b> This population can legitimately shrink to zero — revoke
 * the one workload client and it does. A "must not be empty" assertion would turn that correct
 * state into a red build, which is the "floor under a draining population" failure this repo
 * has already paid for. What is asserted is the property, not the count.
 */
@DisplayName("WorkloadTenantCatalog (TASK-MONO-721 / ADR-MONO-076 D2)")
class WorkloadTenantCatalogTest {

    private static final String MIGRATION_DIR = "src/main/resources/db/migration";
    private static final String EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";

    /**
     * The one client that holds the exchange grant and is deliberately NOT in the catalog.
     *
     * <p>{@code platform-console-web} is an {@code authorization_code} public client — the
     * operator credential. It must take the operator branch, where the fail-closed assignment
     * gate answers. 🔴 Pinning it by name here is intentional: if it ever loses the exchange
     * grant, or if someone adds it to the catalog, this test goes red and a person decides.
     */
    private static final String OPERATOR_EXCHANGE_CLIENT = "platform-console-web";

    @Test
    @DisplayName("모집단 — 교환 grant 를 가진 클라이언트와 카탈로그 키가 **양방향으로** 맞는다")
    void catalogKeysMatchTheExchangeGrantPopulation() {
        Set<String> withExchangeGrant = clientsGrantedTheExchange();

        // Sanity: the parse found something. A silently-empty parse would make every
        // assertion below vacuously true — "0건 ≠ 없음".
        assertThat(withExchangeGrant)
                .as("migrations granting %s", EXCHANGE_GRANT)
                .contains(OPERATOR_EXCHANGE_CLIENT);

        // Direction 1: nothing is enumerated that cannot reach the exchange.
        assertThat(withExchangeGrant)
                .as("every catalog key must hold the exchange grant in the migrations")
                .containsAll(WorkloadTenantCatalog.enumeratedClientIds());

        // Direction 2: every client that CAN reach the exchange has been decided about —
        // either it is in the catalog (a workload, with its tenants) or it is the operator
        // client, which belongs on the other branch. A new client with this grant and no
        // decision fails here rather than silently taking whichever branch it falls into.
        Set<String> undecided = new LinkedHashSet<>(withExchangeGrant);
        undecided.removeAll(WorkloadTenantCatalog.enumeratedClientIds());
        assertThat(undecided)
                .as("clients that can reach the exchange but have no recorded decision")
                .containsExactly(OPERATOR_EXCHANGE_CLIENT);
    }

    @Test
    @DisplayName("🔴 기본값이 안전한 쪽이다 — 열거되지 않은 클라이언트는 **아무 테넌트도** 못 얻는다")
    void anAbsentClientMayAssumeNothing() {
        assertThat(WorkloadTenantCatalog.isWorkloadExchangeClient("account-service-client")).isFalse();
        assertThat(WorkloadTenantCatalog.assumableTenants("account-service-client")).isEmpty();
        assertThat(WorkloadTenantCatalog.mayAssume("account-service-client", "ecommerce")).isFalse();
        // The operator client too — it is not governed by this table at all.
        assertThat(WorkloadTenantCatalog.isWorkloadExchangeClient(OPERATOR_EXCHANGE_CLIENT)).isFalse();
    }

    @Test
    @DisplayName("🔴 대조군 — 열거된 클라이언트도 **자기 집합 밖**은 못 얻는다")
    void anEnumeratedClientIsStillConfinedToItsOwnSet() {
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "ecommerce")).isTrue();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "demo-corp")).isTrue();

        // 🔴 This is the assertion the whole table exists for. TASK-MONO-721 § Failure
        // Scenarios 1: "ⓑ 를 고르고 「워크로드면 전부 허용」으로 구현한다".
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "wms")).isFalse();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "scm")).isFalse();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "iam")).isFalse();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client",
                "global-account-platform")).isFalse();
    }

    @Test
    @DisplayName("🔴 `*` 는 이 규칙의 구현이 아니다 — 어떤 집합도 와일드카드를 담지 않는다")
    void noGrantUsesTheSuperAdminWildcard() {
        // jwt-standard-claims.md carries `*` as the SUPER_ADMIN platform-scope wildcard, and it
        // would make the provisioning call succeed today — by opening EVERY tenant. It was
        // considered and rejected in ADR-MONO-076 § Alternatives. A future edit that reaches
        // for it fails here.
        for (String clientId : WorkloadTenantCatalog.enumeratedClientIds()) {
            assertThat(WorkloadTenantCatalog.assumableTenants(clientId))
                    .as("assumable tenants of %s", clientId)
                    .doesNotContain("*");
        }
    }

    @Test
    @DisplayName("입력이 없거나 비어도 fail-closed")
    void nullAndBlankFailClosed() {
        assertThat(WorkloadTenantCatalog.isWorkloadExchangeClient(null)).isFalse();
        assertThat(WorkloadTenantCatalog.assumableTenants(null)).isEmpty();
        assertThat(WorkloadTenantCatalog.mayAssume(null, "ecommerce")).isFalse();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", null)).isFalse();
        assertThat(WorkloadTenantCatalog.mayAssume("product-service-client", "  ")).isFalse();
    }

    @Test
    @DisplayName("🔵 반환 집합은 불변이다 — 호출자가 카탈로그를 넓힐 수 없다")
    void returnedSetsAreImmutable() {
        Set<String> tenants = WorkloadTenantCatalog.assumableTenants("product-service-client");
        assertThat(tenants).isNotEmpty();
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> tenants.add("wms"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------ migration parsing

    /**
     * Client ids appearing in a migration statement that mentions the exchange grant.
     *
     * <p>Statement-scoped on purpose: {@code V0020} and {@code V0037} are single-client
     * {@code UPDATE … WHERE client_id = '…'}, so attribution is exact. 🔴 <b>Known limit:</b> a
     * future multi-row {@code INSERT} that lists the exchange grant for one tuple would
     * attribute it to every client id in that statement — over-reporting, which fails this test
     * loudly rather than passing it quietly. That is the safe direction, and it is written down
     * rather than left for the next reader to discover.
     */
    private static Set<String> clientsGrantedTheExchange() {
        Path dir = resolveMigrationDir();
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(files).as("migration files under %s", dir).isNotEmpty();

        Pattern clientId = Pattern.compile("client_id\\s*=\\s*'([^']+)'", Pattern.CASE_INSENSITIVE);
        Set<String> found = new LinkedHashSet<>();
        for (Path f : files) {
            String sql = stripComments(read(f));
            for (String statement : sql.split(";")) {
                if (!statement.contains(EXCHANGE_GRANT)) {
                    continue;
                }
                Matcher m = clientId.matcher(statement);
                while (m.find()) {
                    found.add(m.group(1));
                }
            }
        }
        return found;
    }

    /**
     * 🔴 Comments must go first. This file's own header quotes the grant URN while explaining
     * the correction it carries, and {@code V0037}'s does the same — a reader that keeps
     * comments would attribute the grant to whatever client id the prose happens to mention.
     */
    private static String stripComments(String sql) {
        return Stream.of(sql.split("\\R"))
                .map(line -> {
                    int idx = line.indexOf("--");
                    return idx >= 0 ? line.substring(0, idx) : line;
                })
                .collect(Collectors.joining("\n"));
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Gradle may run tests from the module root or the repo root; walk up for the directory. */
    private static Path resolveMigrationDir() {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(MIGRATION_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            Path moduleRelative = cur.resolve("projects/iam-platform/apps/auth-service")
                    .resolve(MIGRATION_DIR);
            if (Files.isDirectory(moduleRelative)) {
                return moduleRelative;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("cannot locate " + MIGRATION_DIR);
    }
}
