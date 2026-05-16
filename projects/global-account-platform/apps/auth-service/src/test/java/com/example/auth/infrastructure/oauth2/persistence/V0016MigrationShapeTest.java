package com.example.auth.infrastructure.oauth2.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-Docker pin test for the corrective Flyway migration
 * {@code V0016__fix_post_logout_redirect_uris_default_typing.sql} (TASK-BE-297).
 *
 * <h3>Why this test exists — the no-op trap it traps</h3>
 *
 * <p>{@code oauth_clients.client_settings} is a MySQL native {@code JSON}
 * column (V0008:32). MySQL normalizes every JSON value on store: object
 * members are re-ordered, whitespace is canonicalized (a space is reinserted
 * after every {@code :} and {@code ,}), and numbers are renormalized.
 * {@code REPLACE(json_col, lit, rep)} additionally casts the JSON to its
 * <em>normalized</em> string form before substituting.
 *
 * <p>The first V0016 attempt used {@code REPLACE()} with the
 * <b>pre-normalization seed literal</b>
 * ({@code "settings.client.post-logout-redirect-uris":["http://localhost:3000/",...]}
 * — no spaces, original key order). That substring never appears in the
 * normalized stored text, so the {@code REPLACE()} and its {@code LIKE} guard
 * matched nothing: a silent no-op. PR #571 surfaced this only after a full CI
 * Testcontainers cycle (3 IT failures) because the existing
 * {@link OAuthClientMapperTest} fed hand-built Java strings and never
 * round-tripped through a MySQL {@code JSON} column.
 *
 * <p>This unit test runs in the {@code :auth-service:test} (no-Docker,
 * no-Testcontainers) lane and pins the structural invariants of the corrected
 * migration so that any regression back to the brittle pre-normalization
 * text-substring approach fails <b>fast at the source</b> — not only after a
 * CI Testcontainers cycle. Same model as {@link BcryptHashPinTest}.
 *
 * <p>It deliberately asserts on migration <em>shape</em> (JSON-structural
 * functions present; pre-normalization {@code REPLACE} literal absent) rather
 * than executing SQL, because the defeating behaviour (MySQL JSON
 * normalization) only exists on a real MySQL {@code JSON} column, which is
 * exactly the Docker-only layer this test is the early-warning for.
 */
class V0016MigrationShapeTest {

    private static final String MIGRATION_RESOURCE =
            "/db/migration/V0016__fix_post_logout_redirect_uris_default_typing.sql";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        try (InputStream in =
                     V0016MigrationShapeTest.class.getResourceAsStream(MIGRATION_RESOURCE)) {
            assertThat(in)
                    .as("V0016 migration must be present on the classpath at " + MIGRATION_RESOURCE)
                    .isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The body (statements only) — comments stripped so assertions ignore the rationale prose. */
    private static String body() {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("--")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @Test
    @DisplayName("V0016 mutates client_settings structurally via MySQL JSON functions (normalization-immune)")
    void usesJsonStructuralFunctions() {
        String body = body();
        assertThat(body)
                .as("corrective UPDATE must rebuild the value with JSON_SET (parsed-tree, "
                        + "immune to MySQL JSON key-reorder/whitespace/number normalization)")
                .contains("JSON_SET(");
        assertThat(body)
                .as("the existing URI array must be lifted verbatim via JSON_EXTRACT so the "
                        + "URIs and their order are preserved byte-for-byte")
                .contains("JSON_EXTRACT(");
        assertThat(body)
                .as("the corrective value must be the SAS-allow-listed [typeId, value] "
                        + "wrapper-array built with JSON_ARRAY")
                .contains("JSON_ARRAY('java.util.ArrayList'");
    }

    @Test
    @DisplayName("V0016 does NOT use the pre-normalization text-substring REPLACE no-op trap")
    void doesNotUsePreNormalizationReplaceLiteral() {
        String body = body();
        // The exact no-op the first attempt shipped: REPLACE() on a JSON column
        // searching for the no-space, original-key-order seed literal. On a
        // MySQL JSON column this never matches the normalized stored text.
        assertThat(body)
                .as("V0016 must not REPLACE() a JSON column on a pre-normalization literal "
                        + "— that is the silent no-op TASK-BE-297 fixes")
                .doesNotContain("REPLACE(");
        assertThat(body)
                .as("the no-space pre-normalization seed substring must not be used as a "
                        + "match/guard target on a MySQL JSON column")
                .doesNotContain(
                        "\"settings.client.post-logout-redirect-uris\":[\"http://localhost:3000/\"");
        assertThat(body)
                .as("a LIKE guard on the pre-normalization literal is equally a no-op on JSON")
                .doesNotContainPattern("(?i)LIKE\\s+'%\"settings\\.client\\.post-logout-redirect-uris\"");
    }

    @Test
    @DisplayName("V0016 corrects exactly the three affected clients, idempotently")
    void targetsThreeAffectedClientsWithIdempotencyGuard() {
        String body = body();
        assertThat(body).contains("'fan-platform-user-flow-client'");   // V0011
        assertThat(body).contains("'ecommerce-web-store-client'");      // V0012
        assertThat(body).contains("'ecommerce-admin-dashboard-client'"); // V0012
        // Idempotency: skip rows already carrying the corrective type id at [0].
        assertThat(body)
                .as("each UPDATE must skip rows whose value is already the corrective "
                        + "['java.util.ArrayList', ...] envelope (idempotent / forward-safe)")
                .contains("<> 'java.util.ArrayList'");
        // Forward-only: no down/rollback statement (data-model.md migration policy).
        assertThat(body.toUpperCase())
                .as("forward-only migration — no DROP/rollback of the corrected value")
                .doesNotContain("ROLLBACK");
    }
}
