package com.example.admin.application;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.infrastructure.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * TASK-BE-298 / ADR-MONO-014 (ACCEPTED) § D2/D3 — GAP OIDC → operator token
 * exchange (RFC 8693). Validates the {@code platform-console-web} subject
 * token, resolves the OIDC subject to an {@code admin_operators} row
 * <b>fail-closed</b>, and mints the existing operator access token through the
 * <b>same</b> issuer the password+TOTP login uses
 * ({@link OperatorAccessTokenIssuer}; architecture.md §Operator-Token Minting
 * Paths) — no claim-assembly duplication.
 *
 * <p><b>Fail-closed invariants</b> (task Acceptance Criteria + Failure
 * Scenarios):
 * <ul>
 *   <li>subject-token validation failure → {@link SubjectTokenInvalidException}
 *       (→ 401 TOKEN_INVALID), no token minted;</li>
 *   <li>no {@code admin_operators} mapping for the OIDC subject → 401, no
 *       token minted;</li>
 *   <li>mapped operator not {@code ACTIVE} (DISABLED/LOCKED) → 401, no token
 *       minted;</li>
 *   <li>tenant scope is NEVER read from the OIDC token — it is resolved at
 *       request time from {@code admin_operators.tenant_id} (ADR-002 sentinel)
 *       by the existing RBAC/tenant evaluators; this service merely mints the
 *       canonical operator token for the resolved operator UUID.</li>
 * </ul>
 *
 * <p>No {@code admin_actions} row is written here (admin-api.md
 * §token-exchange Side Effects: the exchange itself is not audited; subsequent
 * operator commands each audit). No {@code @Transactional}: no DB write, only
 * a fail-closed read + an in-memory mint.
 *
 * <p><b>TASK-MONO-295 (ADR-MONO-040 Phase 2) — DUAL-KEY operator resolution
 * (login-time exchange).</b> Phase 2 flips the SAS access-token {@code sub} to the
 * account UUID, but {@code admin_operators.oidc_subject} is still seeded with the
 * operator's login <b>email</b> (federation {@code seed.sql}). Without the dual-key,
 * {@code findByOidcSubject(account_id)} misses every email-seeded operator → 401
 * {@code TOKEN_INVALID} → console-web {@code not_provisioned} → <b>every operator
 * login breaks</b> (the federation-e2e regression this fixes). This is the
 * login-time sibling of the assume-tenant dual-key already in
 * {@link OperatorAssignmentCheckUseCase}; both delegate to the SHARED
 * {@link OperatorOidcSubjectResolver} so a sub-keyed path cannot silently miss the
 * migration. The legacy email fallback key is resolved <b>server-side</b> from the
 * validated {@code sub} (= account_id): unlike the assume-tenant provider — which
 * reads {@code auth_db.credentials} locally — this exchange is reached
 * <b>directly</b> by console-web and never traverses auth-service, so it resolves the
 * email via the internal auth-service endpoint
 * ({@code GET /internal/auth/credentials/{accountId}/email}, the SAME
 * {@code CredentialRepository.findByAccountId} source). The lookup is <b>fail-soft</b>
 * (auth-service down → empty email → account_id-only resolution); the operator-resolution
 * fail-closed invariant is unchanged (neither key matching → 401, no token).
 * The email is never put on any token and never logged (PII).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private final IamOidcSubjectTokenValidator subjectTokenValidator;
    private final AdminOperatorPort operatorPort;
    private final OperatorAccessTokenIssuer accessTokenIssuer;
    // TASK-MONO-295 (ADR-MONO-040 Phase 2): the SHARED DUAL-KEY resolver — same
    // account_id-first/email-fallback logic the assume-tenant gate uses.
    private final OperatorOidcSubjectResolver operatorResolver;
    // TASK-MONO-295: resolves the legacy email fallback key from the account_id
    // server-side (auth_db.credentials, via the internal auth-service endpoint).
    private final AuthServiceClient authServiceClient;

    /**
     * Exchanges a validated GAP OIDC subject token for an operator access
     * token. The {@code grant_type}/{@code subject_token_type} RFC 8693 shape
     * is validated at the controller (protocol layer); this method receives
     * the raw subject token only.
     *
     * @param subjectToken the GAP OIDC {@code platform-console-web} access token
     * @return the minted operator access token + its TTL
     * @throws SubjectTokenInvalidException on any validation / mapping failure
     *         (fail-closed → 401, no token minted)
     */
    public ExchangeResult exchange(String subjectToken) {
        // 1. Validate the subject token against auth-service JWKS
        //    (iss/aud/exp/nbf/RS256 + token_type-absent guard). Fail-closed.
        String oidcSubject = subjectTokenValidator.validateAndExtractSubject(subjectToken);

        // 2. Resolve the OIDC subject → admin_operators row, FAIL-CLOSED, DUAL-KEY
        //    (ADR-MONO-040 Phase 2). account_id (oidcSubject = sub) first — the
        //    target end-state key; legacy email second — the value oidc_subject is
        //    currently seeded with. The SAS access token carries NO email claim, so
        //    the email fallback is resolved SERVER-SIDE from the validated sub via the
        //    internal auth-service endpoint (auth_db.credentials). The email lookup is
        //    LAZY — invoked by the resolver ONLY when the account_id key misses, so the
        //    happy path (account_id hit / Phase-3 backfilled) never pays the round-trip.
        //    FAIL-SOFT: a failed lookup yields empty → account_id-only resolution (still
        //    fail-closed if THAT misses). The email is never logged (PII) / never on a token.
        AdminOperatorPort.OperatorView operator =
                operatorResolver.resolve(oidcSubject,
                                () -> authServiceClient.resolveOperatorEmail(oidcSubject).orElse(null))
                        .orElseThrow(() -> {
                            log.debug("token-exchange fail-closed: no admin_operators row "
                                    + "for the OIDC subject (account_id key NOR legacy email "
                                    + "fallback matched)");
                            return new SubjectTokenInvalidException(
                                    "No operator is provisioned for this OIDC subject");
                        });

        if (!"ACTIVE".equals(operator.status())) {
            // Deactivated / locked operator → fail-closed, same as no mapping.
            log.debug("token-exchange fail-closed: operator status={} (not ACTIVE)",
                    operator.status());
            throw new SubjectTokenInvalidException(
                    "Operator is not active");
        }

        // 3. Mint the canonical operator token via the SHARED issuer (same
        //    path as login success). Tenant scope is resolved later from
        //    admin_operators.tenant_id by the RBAC/tenant evaluators — it is
        //    never derived from the OIDC token (ADR-MONO-014 D3).
        String accessToken = accessTokenIssuer.mint(operator.operatorId());
        return new ExchangeResult(
                accessToken,
                accessTokenIssuer.accessTokenTtlSeconds());
    }

    /** Successful exchange result: the operator access token + its TTL. */
    public record ExchangeResult(String accessToken, long expiresIn) {}
}
