package com.example.admin.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-634 (and BE-631 before it) — every audit-row key admin-service builds must fit
 * {@code admin_actions.idempotency_key VARCHAR(100)} for the widest legal input. An overflow does not lose a row:
 * the fail-closed audit aborts the whole command as {@code 500 AUDIT_FAILURE}, and the unit tests that mock the
 * auditor cannot see it. Two keys shipped that way — assignment (over the column past an 11-char tenant id) and
 * partnership participant (over the column on every call).
 *
 * <p><b>A new key builder belongs in {@link AuditIdempotencyKeys} and in {@link #everyBuilderFitsTheColumn()}.</b>
 * {@link #noKeyIsBuiltOutsideTheBuilderClass()} fails when someone concatenates a key at a call site instead, because
 * that is exactly how the two overflowing keys were written.
 */
class AuditIdempotencyKeysTest {

    private static final String UUID_36 = "0192f3a0-0000-7000-8000-000000000001";
    private static final String TENANT_32 = "t".repeat(32);
    private static final Instant FAR_FUTURE = Instant.parse("2286-11-20T17:46:39.999Z"); // 13-digit epoch millis

    @Test
    @DisplayName("every builder, fed the widest legal ids (36-char UUIDs · 32-char tenant · 13-digit millis), fits 100")
    void everyBuilderFitsTheColumn() {
        assertThat(String.valueOf(FAR_FUTURE.toEpochMilli())).hasSize(13);
        List<String> keys = new ArrayList<>(List.of(
                AuditIdempotencyKeys.assignment(UUID_36, TENANT_32, FAR_FUTURE),
                AuditIdempotencyKeys.orgScope(UUID_36, TENANT_32, FAR_FUTURE),
                AuditIdempotencyKeys.participant(UUID_36, UUID_36, FAR_FUTURE),
                AuditIdempotencyKeys.identityLink(UUID_36, FAR_FUTURE),
                AuditIdempotencyKeys.identityUnlink(UUID_36, FAR_FUTURE),
                AuditIdempotencyKeys.adminProfileUpdate(UUID_36, FAR_FUTURE),
                AuditIdempotencyKeys.selfProfileUpdate(UUID_36, FAR_FUTURE)));
        // The partnership key carries the action name; take every PARTNERSHIP_* code, not the one that looks longest.
        Arrays.stream(ActionCode.values())
                .filter(c -> c.name().startsWith("PARTNERSHIP_"))
                .forEach(c -> keys.add(AuditIdempotencyKeys.partnership(c, UUID_36, FAR_FUTURE)));

        assertThat(keys).hasSizeGreaterThan(8);
        assertThat(keys).allSatisfy(k -> assertThat(k).hasSizeLessThanOrEqualTo(AuditIdempotencyKeys.COLUMN));
    }

    @Test
    @DisplayName("control: the pre-fix participant and assignment forms overflow the column for the same inputs")
    void preFixFormsOverflowed() {
        long millis = FAR_FUTURE.toEpochMilli();
        String participantAdd = "participant:PARTNERSHIP_PARTICIPANT_ADD:" + UUID_36 + ":" + UUID_36 + ":" + millis;
        String participantRemove = "participant:PARTNERSHIP_PARTICIPANT_REMOVE:" + UUID_36 + ":" + UUID_36 + ":" + millis;
        String assignment = "assignment:OPERATOR_ASSIGNMENT_DELETE:" + UUID_36 + ":" + TENANT_32 + ":" + millis;
        assertThat(participantAdd).hasSize(127);
        assertThat(participantRemove).hasSize(130);
        assertThat(assignment.length()).isGreaterThan(AuditIdempotencyKeys.COLUMN);
    }

    @Test
    @DisplayName("keys still separate their subjects (the unique index adds actor and action code)")
    void keysSeparateTheirSubjects() {
        Instant at = Instant.parse("2026-10-14T00:00:00Z");
        assertThat(AuditIdempotencyKeys.assignment(UUID_36, "acme-corp", at))
                .isEqualTo("asg:" + UUID_36 + ":acme-corp:" + at.toEpochMilli())
                .isNotEqualTo(AuditIdempotencyKeys.assignment(UUID_36, "globex", at));
        String other = "0192f3a0-0000-7000-8000-000000000002";
        assertThat(AuditIdempotencyKeys.participant(UUID_36, UUID_36, at))
                .isEqualTo("ptc:" + UUID_36 + ":" + UUID_36 + ":" + at.toEpochMilli())
                .isNotEqualTo(AuditIdempotencyKeys.participant(UUID_36, other, at))
                .isNotEqualTo(AuditIdempotencyKeys.participant(other, UUID_36, at));
    }

    @Test
    @DisplayName("no main-source line outside AuditIdempotencyKeys concatenates a \":\"-joined key ending in toEpochMilli()")
    void noKeyIsBuiltOutsideTheBuilderClass() throws IOException {
        Path root = Path.of("src/main/java");
        assertThat(root).isDirectory(); // the scan must look somewhere real, or it passes on nothing
        List<String> offenders = new ArrayList<>();
        int scanned;
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            scanned = sources.size();
            for (Path p : sources) {
                if (p.getFileName().toString().equals("AuditIdempotencyKeys.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.contains("\":\"") && line.contains("toEpochMilli()")) {
                        offenders.add(p.getFileName() + ":" + (i + 1) + "  " + line.trim());
                    }
                }
            }
        }
        assertThat(scanned).isGreaterThan(100);
        assertThat(offenders).as("build the key in AuditIdempotencyKeys and add it to everyBuilderFitsTheColumn")
                .isEmpty();
    }
}
