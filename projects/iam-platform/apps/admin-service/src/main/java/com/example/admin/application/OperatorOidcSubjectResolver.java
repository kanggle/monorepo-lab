package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * TASK-MONO-295 (ADR-MONO-040 Phase 2) — the single, shared DUAL-KEY
 * OIDC-subject → operator resolver.
 *
 * <p>Phase 2 flips the SAS access-token {@code sub} to the account UUID, but
 * {@code admin_operators.oidc_subject} is still seeded with the operator's login
 * <b>email</b> (federation {@code seed.sql}), and the account_id↔email mapping lives
 * in {@code auth_db} — a physically separate database from {@code admin_db} — so a
 * single-Flyway backfill is impossible (mirrors the V0036 "no cross-DB read at
 * migrate time" precedent). To keep <b>every existing operator working</b> while the
 * {@code sub} migration lands, the operator is resolved by the account_id
 * {@code oidcSubject} FIRST (the target end-state key), and on a miss falls back to
 * the legacy {@code subjectEmail} (the seed value).
 *
 * <p>Account_id is the preferred key: once the eventual cross-DB backfill sets
 * {@code oidc_subject}=account_id (Phase-3 follow-up), the primary lookup hits and the
 * email fallback silently stops being consulted. The fallback never relaxes the
 * fail-closed gate — a row that matches NEITHER key resolves to empty (the caller
 * treats that as 401 / not-assigned, never leaking operator existence).
 *
 * <p><b>Why a shared component</b>: BOTH operator-token exchanges resolve the operator
 * by the subject token's {@code sub} and must apply the IDENTICAL dual-key fallback —
 * the assume-tenant exchange ({@link OperatorAssignmentCheckUseCase}, auth-service →
 * admin {@code /internal/operator-assignments/check}) AND the login-time exchange
 * ({@link TokenExchangeService}, {@code POST /api/admin/auth/token-exchange}). Centralising
 * the resolution here means a THIRD sub-keyed path cannot silently miss the migration
 * (the federation-e2e regression this fixes was exactly such a divergence: the
 * assume-tenant path had the dual-key but the login-time path did not).
 *
 * <p>Both keys are looked up against the SAME platform-global UNIQUE
 * {@code oidc_subject} column (V0027), so at most one row matches either key. A
 * blank/null key is skipped. The {@code subjectEmail} is {@code confidential} PII —
 * never logged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorOidcSubjectResolver {

    private final AdminOperatorPort operatorPort;

    /**
     * Resolve the {@code admin_operators} row for an OIDC subject, DUAL-KEY.
     *
     * @param oidcSubject  the operator's GAP OIDC {@code sub} (account_id — the
     *                     preferred/end-state key)
     * @param subjectEmail the operator's login email (the legacy fallback key the
     *                     {@code oidc_subject} column is currently seeded with; may be
     *                     {@code null}/blank — then only the account_id key is tried)
     * @return the resolved operator row, or empty when NEITHER key matches a row
     *         (the caller fail-closes — 401 / not-assigned)
     */
    public Optional<AdminOperatorPort.OperatorView> resolve(String oidcSubject, String subjectEmail) {
        return resolve(oidcSubject, () -> subjectEmail);
    }

    /**
     * DUAL-KEY resolution with a <b>lazy</b> email fallback: the
     * {@code emailSupplier} is invoked ONLY when the account_id key misses, so the
     * happy path (account_id hit — the Phase-3 backfill end-state) never pays the
     * email-resolution cost (e.g. the login-time exchange's server-side
     * auth-service round-trip). The supplier may return {@code null} (no email
     * resolvable / fail-soft).
     *
     * @param oidcSubject   the account_id key (preferred)
     * @param emailSupplier supplies the legacy email fallback key, invoked at most
     *                      once and only on an account_id miss
     */
    public Optional<AdminOperatorPort.OperatorView> resolve(String oidcSubject,
                                                            Supplier<String> emailSupplier) {
        if (oidcSubject != null && !oidcSubject.isBlank()) {
            Optional<AdminOperatorPort.OperatorView> byAccountId =
                    operatorPort.findByOidcSubject(oidcSubject);
            if (byAccountId.isPresent()) {
                return byAccountId;
            }
        }
        // Phase-2 transition fallback: oidc_subject still holds the login email.
        // Resolve the email lazily — only now that the account_id key missed.
        String subjectEmail = emailSupplier == null ? null : emailSupplier.get();
        if (subjectEmail != null && !subjectEmail.isBlank()) {
            Optional<AdminOperatorPort.OperatorView> byEmail =
                    operatorPort.findByOidcSubject(subjectEmail);
            if (byEmail.isPresent()) {
                log.debug("operator resolved via the legacy email fallback "
                        + "(oidc_subject not yet backfilled to account_id)");
                return byEmail;
            }
        }
        return Optional.empty();
    }
}
