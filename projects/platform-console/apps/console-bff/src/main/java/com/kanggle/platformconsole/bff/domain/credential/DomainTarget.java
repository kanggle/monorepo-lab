package com.kanggle.platformconsole.bff.domain.credential;

/**
 * The 5 backend domain targets the BFF fans out to.
 *
 * <p>Used by {@link CredentialSelectionPort} to select the outbound credential
 * per-domain (ADR-MONO-017 D4.A HARD INVARIANT).
 *
 * <p>Selector predicate (byte-verbatim from {@code console-integration-contract.md} § 2.4.9):
 * <ul>
 *   <li>{@code GAP} → {@link OutboundCredential.OperatorToken} (RFC 8693 exchanged)</li>
 *   <li>{@code WMS, SCM, FINANCE, ERP} → {@link OutboundCredential.IamOidcAccessToken}</li>
 * </ul>
 */
public enum DomainTarget {
    GAP,
    WMS,
    SCM,
    FINANCE,
    ERP
}
