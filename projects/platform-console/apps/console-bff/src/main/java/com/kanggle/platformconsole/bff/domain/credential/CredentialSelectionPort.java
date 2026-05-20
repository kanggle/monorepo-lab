package com.kanggle.platformconsole.bff.domain.credential;

/**
 * Port: selects the outbound credential for a given domain target.
 *
 * <p>The selector predicate is verbatim from
 * {@code console-integration-contract.md} § 2.4.9:
 * <pre>
 *   GAP              → OperatorToken   (RFC 8693 exchanged)
 *   WMS, SCM, FINANCE, ERP → GapOidcAccessToken
 * </pre>
 *
 * <p>There is NO fallback path. If the required token is absent the
 * implementation throws {@link MissingCredentialException} — callers MUST NOT
 * attempt to retry with the other token type. This is the #569 invariant
 * (ADR-MONO-017 D4.A HARD INVARIANT).
 *
 * <p>Implemented by:
 * {@code adapter.outbound.http.CredentialSelectionAdapter} (reads from the
 * request-scoped {@code OperatorCredentialContext}).
 */
public interface CredentialSelectionPort {

    /**
     * Returns the credential to attach to an outbound HTTP call targeting
     * {@code domain}.
     *
     * @param domain the target domain
     * @return the sealed outbound credential (never null)
     * @throws MissingCredentialException if the required token is absent
     */
    OutboundCredential selectFor(DomainTarget domain);
}
