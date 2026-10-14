package com.example.admin.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-635 — every {@code @RequestHeader("Idempotency-Key")} a controller declares must either carry a
 * {@link ClientIdempotencyKey}-backed {@code @Size} bound, or be in {@link #DOWNSTREAM_VALIDATED_FILES} with a
 * comment naming the use-case check that already bounds it (the pre-existing {@code MAX_IDEMPOTENCY_KEY = 100}
 * pattern in {@code DomainPermissionSetUseCase} / {@code GroupDomainPermissionSetUseCase} /
 * {@code OperatorInvitationUseCase}, which this ticket does not touch).
 *
 * <p>Why it matters: an unbounded client header reaches {@code admin_actions.idempotency_key VARCHAR(100)}
 * verbatim (or concatenated — bulk-lock, tenant-update). Over-length used to overflow the column, and the
 * fail-closed audit write turned that <b>client input error</b> into {@code 500 AUDIT_FAILURE}
 * instead of {@code 400 VALIDATION_ERROR}.
 *
 * <p><b>A new {@code @RequestHeader("Idempotency-Key")} belongs with a {@code @Size(max = ClientIdempotencyKey.…)}
 * on the same parameter</b> — {@link #everyIdempotencyKeyHeaderCarriesALengthBoundOrIsDownstreamValidated()} fails
 * otherwise, the same discipline as {@code AuditIdempotencyKeysTest} (TASK-BE-634) for server-built keys.
 */
class ClientIdempotencyKeyTest {

    /**
     * Controllers whose {@code Idempotency-Key} header is {@code required = false} and bounded in the
     * use case instead of at the controller boundary (pre-existing {@code MAX_IDEMPOTENCY_KEY = 100},
     * out of this ticket's scope — see the task's Related Specs / Goal).
     */
    private static final Set<String> DOWNSTREAM_VALIDATED_FILES = Set.of(
            "DomainPermissionSetController.java",   // DomainPermissionSetUseCase.MAX_IDEMPOTENCY_KEY
            "GroupDomainPermissionSetController.java", // GroupDomainPermissionSetUseCase.MAX_IDEMPOTENCY_KEY
            "OperatorInvitationController.java");   // OperatorInvitationUseCase.MAX_IDEMPOTENCY_KEY

    @Test
    @DisplayName("the bulk-lock and tenant-update constants still fit the column after their suffix/concatenation")
    void constantsAreConsistentWithTheColumn() {
        assertThat(ClientIdempotencyKey.BULK_LOCK_HEADER_MAX + 1 + ClientIdempotencyKey.BULK_LOCK_ACCOUNT_ID_MAX)
                .as("header + \":\" + accountId must fit VARCHAR(100) exactly at the widest legal accountId")
                .isEqualTo(ClientIdempotencyKey.MAX_LENGTH);
        assertThat(ClientIdempotencyKey.TENANT_UPDATE_HEADER_MAX + ":status".length())
                .as("UpdateTenantUseCase appends \":status\" to the header for the status-change audit row")
                .isEqualTo(ClientIdempotencyKey.MAX_LENGTH);
    }

    @Test
    @DisplayName("every @RequestHeader(\"Idempotency-Key\") carries a @Size bound, or is an acknowledged "
            + "downstream-validated exception")
    void everyIdempotencyKeyHeaderCarriesALengthBoundOrIsDownstreamValidated() throws IOException {
        Path root = Path.of("src/main/java");
        assertThat(root).isDirectory(); // the scan must look somewhere real, or it passes on nothing
        List<String> offenders = new ArrayList<>();
        int scanned;
        int declarationsFound = 0;
        try (Stream<Path> files = Files.walk(root)) {
            List<Path> sources = files.filter(p -> p.toString().endsWith(".java")).toList();
            scanned = sources.size();
            for (Path p : sources) {
                // Excluded: this is the constants/convention file itself, whose javadoc quotes the
                // annotation literally ("A new @RequestHeader(\"Idempotency-Key\") must bound...") —
                // that is prose, not a declaration (mirrors AuditIdempotencyKeysTest excluding its
                // own builder class).
                if (p.getFileName().toString().equals("ClientIdempotencyKey.java")) {
                    continue;
                }
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    if (isIdempotencyKeyHeaderDeclaration(lines.get(i))) {
                        declarationsFound++;
                        boolean bounded = hasNearbySize(lines, i);
                        boolean exempt = DOWNSTREAM_VALIDATED_FILES.contains(p.getFileName().toString());
                        if (!bounded && !exempt) {
                            offenders.add(p.getFileName() + ":" + (i + 1) + "  " + lines.get(i).trim());
                        }
                    }
                }
            }
        }
        assertThat(scanned).isGreaterThan(100);
        assertThat(declarationsFound)
                .as("the scan itself must find declarations, or a passing offenders-list proves nothing")
                .isGreaterThanOrEqualTo(10);
        assertThat(offenders)
                .as("bound the header with a ClientIdempotencyKey constant (@Size), or add the file to "
                        + "DOWNSTREAM_VALIDATED_FILES with a comment naming the use-case check that bounds it")
                .isEmpty();
    }

    @Test
    @DisplayName("control: the predicate catches the exact pre-fix (single-line, unbounded) declaration form")
    void predicateCatchesThePreFixForm() {
        // Copied verbatim from AccountAdminController#lock before this ticket's fix.
        String preFixLine = "            @RequestHeader(\"Idempotency-Key\") String idempotencyKey,";
        assertThat(isIdempotencyKeyHeaderDeclaration(preFixLine)).isTrue();
        assertThat(hasNearbySize(List.of(preFixLine), 0))
                .as("the pre-fix line has no @Size anywhere nearby")
                .isFalse();
    }

    @Test
    @DisplayName("control: the predicate accepts this ticket's two-line fixed form (@RequestHeader then @Size)")
    void predicateAcceptsTheFixedForm() {
        List<String> fixedLines = List.of(
                "            @RequestHeader(\"Idempotency-Key\")",
                "            @Size(max = ClientIdempotencyKey.MAX_LENGTH, message = \"Idempotency-Key must be ≤100 characters\")",
                "            String idempotencyKey,");
        assertThat(isIdempotencyKeyHeaderDeclaration(fixedLines.get(0))).isTrue();
        assertThat(hasNearbySize(fixedLines, 0)).isTrue();
    }

    private static boolean isIdempotencyKeyHeaderDeclaration(String line) {
        return line.contains("@RequestHeader") && line.contains("Idempotency-Key");
    }

    /**
     * Looks at the declaration line and up to {@code LOOKAHEAD} lines after it — covers the one-line form,
     * the two-line form, and this ticket's commented form (a multi-line explanatory comment between
     * {@code @RequestHeader} and {@code @Size}, e.g. {@code AccountAdminController#bulkLock}).
     */
    private static final int LOOKAHEAD = 8;

    private static boolean hasNearbySize(List<String> lines, int index) {
        int end = Math.min(lines.size(), index + LOOKAHEAD);
        for (int j = index; j < end; j++) {
            if (lines.get(j).contains("@Size")) {
                return true;
            }
        }
        return false;
    }
}
