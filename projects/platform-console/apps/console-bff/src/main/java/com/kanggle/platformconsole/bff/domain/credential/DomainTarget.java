package com.kanggle.platformconsole.bff.domain.credential;

/**
 * The 6 backend domain targets the BFF fans out to.
 *
 * <p>Used by {@link CredentialSelectionPort} to select the outbound credential
 * per-domain (ADR-MONO-017 D4.A HARD INVARIANT).
 *
 * <p>Selector predicate (byte-verbatim from {@code console-integration-contract.md} § 2.4.9):
 * <ul>
 *   <li>{@code IAM} → {@link OutboundCredential.OperatorToken} (RFC 8693 exchanged)</li>
 *   <li>{@code WMS, SCM, FINANCE, ERP, ECOMMERCE} → {@link OutboundCredential.IamOidcAccessToken}</li>
 * </ul>
 *
 * <p><b>Enum-order invariant (TASK-MONO-241)</b>: {@code ECOMMERCE} is the
 * <b>last</b> member. The two composition use-cases key their leg maps in
 * {@link java.util.EnumMap}s, whose iteration order is enum declaration order;
 * appending {@code ECOMMERCE} keeps the existing 5 domains' iteration order
 * byte-stable, so the 5-leg Operator Overview (§ 2.4.9.1) is unaffected while
 * the 6-leg Domain Health Overview (§ 2.4.9.2) adds the ecommerce card last.
 */
public enum DomainTarget {
    IAM,
    WMS,
    SCM,
    FINANCE,
    ERP,
    ECOMMERCE
}
