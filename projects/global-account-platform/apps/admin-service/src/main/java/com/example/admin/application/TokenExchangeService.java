package com.example.admin.application;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.GapOidcSubjectTokenValidator;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExchangeService {

    private final GapOidcSubjectTokenValidator subjectTokenValidator;
    private final AdminOperatorPort operatorPort;
    private final OperatorAccessTokenIssuer accessTokenIssuer;

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

        // 2. Resolve the OIDC subject → admin_operators row, FAIL-CLOSED.
        //    Missing mapping OR non-ACTIVE operator → 401, no token.
        AdminOperatorPort.OperatorView operator = operatorPort.findByOidcSubject(oidcSubject)
                .orElseThrow(() -> {
                    log.debug("token-exchange fail-closed: no admin_operators row "
                            + "for the OIDC subject");
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
