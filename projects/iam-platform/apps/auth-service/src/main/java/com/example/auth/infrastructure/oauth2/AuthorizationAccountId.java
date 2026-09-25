package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.session.PrincipalDetailKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.security.Principal;
import java.util.Map;

/**
 * TASK-BE-603 — the account identity a SAS authorization speaks for.
 *
 * <p><b>Why not {@link OAuth2Authorization#getPrincipalName()}.</b> Both browser login paths
 * ({@code CredentialAuthenticationProvider} and {@code SocialLoginBrowserController}) build the
 * resource-owner principal as {@code UsernamePasswordAuthenticationToken(email, …)}, so the
 * principal name is the <em>login email</em>. The account UUID rides on the principal's
 * {@code details} map under {@link PrincipalDetailKeys#ACCOUNT_ID}. Writing the principal name
 * into {@code refresh_tokens.account_id} (VARCHAR(36)) put an email there: an email longer than
 * 36 characters made every refresh of that session fail, {@code revokeAllByAccountId(uuid)}
 * never matched the row, and every event built from it carried the email as {@code accountId}.
 *
 * <p>SAS stores the resource-owner {@link Authentication} on the authorization under
 * {@code java.security.Principal} — the key its authorization-code provider writes (see
 * TASK-BE-465).
 */
@Slf4j
final class AuthorizationAccountId {

    private AuthorizationAccountId() {
    }

    /**
     * The account UUID the login path stored on the resource-owner principal, or {@code null}
     * when the authorization carries none.
     */
    static String fromPrincipalDetails(OAuth2Authorization authorization) {
        Object principal = authorization.getAttribute(Principal.class.getName());
        if (principal instanceof Authentication authentication
                && authentication.getDetails() instanceof Map<?, ?> details) {
            Object accountId = details.get(PrincipalDetailKeys.ACCOUNT_ID);
            return accountId instanceof String s && !s.isBlank() ? s : null;
        }
        return null;
    }

    /**
     * The account key for the {@code refresh_tokens} mirror row and for the events and
     * revocations built from it.
     *
     * <p>= the principal details' {@code account_id}. When the principal carries none, the
     * principal name is used — the same rule {@code TenantClaimTokenCustomizer#alignSubToAccountId}
     * applies to the token's {@code sub}, so the mirror row stays keyed on the identity the
     * session's own tokens carry. Neither production login path produces such a principal; it
     * is logged at WARN so that a new producer that forgets the detail is visible rather than
     * silently reintroducing an email-valued key.
     */
    static String forMirrorRow(OAuth2Authorization authorization) {
        String accountId = fromPrincipalDetails(authorization);
        if (accountId != null) {
            return accountId;
        }
        log.warn("SAS authorization={} carries no account_id in its principal details — "
                        + "keying its refresh-token mirror row on the principal name instead",
                authorization.getId());
        return authorization.getPrincipalName();
    }
}
