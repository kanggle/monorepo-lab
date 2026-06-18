package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorOidcSubjectBackfillUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): internal maintenance endpoint that
 * runs the idempotent {@code admin_operators.oidc_subject} email→account_id
 * backfill (the production-shaped runnable mechanism — not a doc-only SQL recipe).
 *
 * <p>URL: {@code POST /internal/admin/operator-oidc-subject-backfill}
 *
 * <p>Authentication: under the {@code @Order(0)} {@code /internal/**}
 * resource-server chain — GAP {@code client_credentials} Bearer JWT (fail-closed;
 * the test/standalone bypass profile populates an authenticated principal). The
 * operator {@code /api/admin/**} chain does NOT match this path. Mirrors
 * {@link OperatorAssignmentCheckController}'s security/wiring.
 *
 * <p><b>Idempotent</b> (AC-2): only email-shaped {@code oidc_subject} rows are
 * migrated; re-running is a no-op for already-migrated (UUID-shaped) rows.
 * <b>Fail-soft</b> (AC-3): an operator whose account_id cannot be resolved
 * (no credential / ambiguous email / auth-service unavailable) is left unchanged
 * and counted — it stays resolvable via the RETAINED email fallback. The email
 * VALUE is never logged (PII) — only the key-shape transition (AC-2).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/admin")
public class OperatorOidcSubjectBackfillController {

    private final OperatorOidcSubjectBackfillUseCase backfillUseCase;

    /**
     * Run the backfill and return the report.
     *
     * @return {@code 200} with {@code {scanned, updated, skippedAlreadyUuid,
     *         skippedNull, unresolved}}
     */
    @PostMapping("/operator-oidc-subject-backfill")
    public ResponseEntity<OperatorOidcSubjectBackfillUseCase.BackfillReport> backfill() {
        return ResponseEntity.ok(backfillUseCase.run());
    }
}
