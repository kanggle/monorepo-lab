package com.kanggle.platformconsole.bff.domain.credential;

/**
 * Sealed hierarchy of outbound credentials the BFF may dispatch per domain.
 *
 * <p>ADR-MONO-017 D4.A HARD INVARIANT — the BFF is the credential <em>dispatcher</em>,
 * never its rewriter. Two credential types exist:
 * <ul>
 *   <li>{@link OperatorToken} — RFC 8693 exchanged operator token, used exclusively for
 *       the GAP admin surface ({@code /api/admin/**}).</li>
 *   <li>{@link GapOidcAccessToken} — GAP OIDC access token, used for non-GAP domains
 *       (wms / scm / finance / erp).</li>
 * </ul>
 *
 * <p>No fallback path: missing operator token on a GAP-targeted dispatch fails closed
 * (callers must check for absent tokens before invoking
 * {@link CredentialSelectionPort#selectFor(DomainTarget)} — the port itself asserts
 * non-null via the record components).
 */
public sealed interface OutboundCredential
        permits OutboundCredential.OperatorToken, OutboundCredential.GapOidcAccessToken {

    /**
     * RFC 8693 exchanged operator token.
     * Used for GAP domain only ({@code Authorization: Bearer <operator-token>}).
     */
    record OperatorToken(String token) implements OutboundCredential {
        public OperatorToken {
            if (token == null || token.isBlank()) {
                throw new MissingCredentialException(
                        "Operator token is absent — GAP leg dispatch fails closed (ADR-MONO-017 D4.A)");
            }
        }
    }

    /**
     * GAP OIDC access token (RS256).
     * Used for non-GAP domains — wms / scm / finance / erp.
     */
    record GapOidcAccessToken(String token) implements OutboundCredential {
        public GapOidcAccessToken {
            if (token == null || token.isBlank()) {
                throw new MissingCredentialException(
                        "GAP OIDC access token is absent — non-GAP leg dispatch fails closed");
            }
        }
    }
}
