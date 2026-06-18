package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.infrastructure.client.AuthServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TASK-MONO-298 (ADR-MONO-040 Phase 3 part A) — the idempotent, production-shaped
 * one-time backfill that migrates {@code admin_operators.oidc_subject} from the
 * operator's login <b>email</b> (the Phase-2 seed value) to its <b>account_id</b>
 * (the Phase-3 end-state key), so the dual-key resolver's account_id-first lookup
 * hits directly. The email→account_id mapping lives in {@code auth_db.credentials}
 * — a physically separate database from {@code admin_db} — so this is NOT a Flyway
 * step; each account_id is resolved at run time via the internal auth-service
 * endpoint ({@link AuthServiceClient#resolveOperatorAccountId}).
 *
 * <h2>Idempotency (AC-2)</h2>
 *
 * <p>Only <b>email-shaped</b> {@code oidc_subject} values are processed: non-null,
 * contains {@code @}, and not UUID-parseable. A row already migrated (UUID-shaped)
 * or unprovisioned (null) is skipped. Re-running after a partial backfill is a
 * no-op for already-migrated rows — only the remaining email-shaped rows are
 * processed.
 *
 * <h2>Fail-soft (AC-3)</h2>
 *
 * <p>When auth-service returns no account_id (no credential / ambiguous email /
 * auth-service unavailable), the operator's {@code oidc_subject} is left
 * <b>unchanged</b> and counted under {@code unresolved} — it stays resolvable via
 * the RETAINED email fallback (no operator regresses). One failure never aborts the
 * batch.
 *
 * <h2>Tenant scoping (AC-1 CRITICAL)</h2>
 *
 * <p>The operator's {@code tenant_id} is passed to the auth-service lookup because
 * {@code credentials.email} is unique only per tenant ({@code uk_credentials_tenant_email}).
 * A wrong account_id would mis-authorize the operator; the scoped resolution (plus
 * the auth-service global-unambiguity guard) prevents a cross-tenant mis-resolution.
 *
 * <h2>Audit without PII (AC-2)</h2>
 *
 * <p>Each successful update is audit-logged with the operator id and the key-shape
 * transition ({@code email-shaped → account_id}). The email VALUE is NEVER logged
 * ({@code confidential} PII) — only the fact it was email-shaped. admin-service's
 * {@code admin_actions} audit subsystem is operator-context- and ActionCode-bound
 * (built for operator-initiated commands); this machine-invoked maintenance batch
 * has no operator context, so the audit is emitted via the structured application
 * log (the audit trail surface for an internal maintenance run), consistent with
 * the ADR-014 token-exchange "not an {@code admin_actions} row" precedent.
 */
@Slf4j
@Service
public class OperatorOidcSubjectBackfillUseCase {

    private final AdminOperatorPort operatorPort;
    private final AuthServiceClient authServiceClient;

    /**
     * Request clock for the {@code updated_at} stamp. Defaults to system UTC; a
     * unit test may supply a fixed clock via the all-args constructor.
     */
    private final Clock clock;

    /**
     * Production constructor — system UTC clock (no required {@code Clock} bean).
     * {@code @Autowired} marks this as the injection constructor: the class has a
     * second (package-private, fixed-clock) constructor for tests, so Spring would
     * otherwise fall back to a non-existent no-arg constructor (the IT context-load
     * failure this annotation fixes).
     */
    @Autowired
    public OperatorOidcSubjectBackfillUseCase(AdminOperatorPort operatorPort,
                                              AuthServiceClient authServiceClient) {
        this(operatorPort, authServiceClient, Clock.systemUTC());
    }

    /** Test constructor — fixed clock for deterministic {@code updated_at}. */
    OperatorOidcSubjectBackfillUseCase(AdminOperatorPort operatorPort,
                                       AuthServiceClient authServiceClient,
                                       Clock clock) {
        this.operatorPort = operatorPort;
        this.authServiceClient = authServiceClient;
        this.clock = clock;
    }

    /**
     * Run the backfill across every provisioned operator.
     *
     * @return the report: {@code scanned} (rows with a non-null oidc_subject),
     *         {@code updated}, {@code skippedAlreadyUuid}, {@code skippedNull}
     *         (always 0 here — null rows are not enumerated, retained for contract
     *         symmetry), {@code unresolved}
     */
    public BackfillReport run() {
        Instant now = clock.instant();
        List<AdminOperatorPort.OperatorOidcSubjectView> operators =
                operatorPort.findOperatorsWithOidcSubject();

        int scanned = 0;
        int updated = 0;
        int skippedAlreadyUuid = 0;
        int skippedNull = 0;
        int unresolved = 0;

        for (AdminOperatorPort.OperatorOidcSubjectView op : operators) {
            String subject = op.oidcSubject();
            if (subject == null || subject.isBlank()) {
                // Defensive — the finder excludes nulls, but a blank is treated as null.
                skippedNull++;
                continue;
            }
            scanned++;

            if (!isEmailShaped(subject)) {
                // UUID-parseable (or any non-email shape) → already migrated / not our concern.
                skippedAlreadyUuid++;
                continue;
            }

            // email-shaped → resolve account_id, tenant-scoped. fail-soft.
            Optional<String> accountId =
                    authServiceClient.resolveOperatorAccountId(subject, op.tenantId());
            if (accountId.isEmpty()) {
                unresolved++;
                log.info("[oidc-subject-backfill] operator_id={} tenant={} oidc_subject=<email-shaped> "
                                + "→ UNRESOLVED (left unchanged; stays on retained email fallback)",
                        op.operatorId(), op.tenantId());
                continue;
            }

            operatorPort.updateOidcSubject(op.internalId(), accountId.get(), now);
            updated++;
            // PII-safe audit: NEVER the email value — only the key-shape transition.
            log.info("[oidc-subject-backfill] operator_id={} tenant={} oidc_subject migrated "
                            + "email-shaped → account_id (account_id={})",
                    op.operatorId(), op.tenantId(), accountId.get());
        }

        BackfillReport report = new BackfillReport(
                scanned, updated, skippedAlreadyUuid, skippedNull, unresolved);
        log.info("[oidc-subject-backfill] complete: {}", report);
        return report;
    }

    /**
     * Email-shape detection: non-null, contains {@code @}, and NOT a parseable UUID.
     * A UUID-parseable value (even if it somehow contained {@code @}, which it cannot)
     * is treated as already migrated.
     */
    static boolean isEmailShaped(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!value.contains("@")) {
            return false;
        }
        try {
            UUID.fromString(value);
            return false; // parseable UUID → already migrated
        } catch (IllegalArgumentException notUuid) {
            return true;
        }
    }

    /**
     * Idempotent backfill report (AC-2). {@code scanned} = rows with a non-null
     * {@code oidc_subject}; {@code updated} = email-shaped rows migrated to
     * account_id; {@code skippedAlreadyUuid} = already-migrated (UUID-shaped) rows;
     * {@code skippedNull} = blank rows (defensive; the finder excludes true nulls);
     * {@code unresolved} = email-shaped rows auth-service could not resolve (left
     * unchanged, fail-soft).
     */
    public record BackfillReport(
            int scanned,
            int updated,
            int skippedAlreadyUuid,
            int skippedNull,
            int unresolved
    ) {}
}
