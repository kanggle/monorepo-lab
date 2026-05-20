package com.kanggle.platformconsole.bff.adapter.outbound.http;

import com.kanggle.platformconsole.bff.domain.credential.CredentialSelectionPort;
import com.kanggle.platformconsole.bff.domain.credential.DomainTarget;
import com.kanggle.platformconsole.bff.domain.credential.OutboundCredential;
import org.springframework.stereotype.Component;

/**
 * Adapter: implements {@link CredentialSelectionPort} using the request-scoped
 * {@link OperatorCredentialContext}.
 *
 * <p>Selector predicate — verbatim from {@code console-integration-contract.md} § 2.4.9
 * table (ADR-MONO-017 D4.A HARD INVARIANT):
 * <pre>
 *   GAP              → OperatorToken(operatorToken)
 *   WMS, SCM, FINANCE, ERP → GapOidcAccessToken(gapOidcAccessToken)
 * </pre>
 *
 * <p>NO fallback path. If the required token is absent, the record constructor
 * in {@link OutboundCredential} throws {@code MissingCredentialException} immediately.
 * The switch is exhaustive (5 domains, no default arm) — adding a 6th domain without
 * updating this switch causes a compile error.
 */
@Component
public class CredentialSelectionAdapter implements CredentialSelectionPort {

    private final OperatorCredentialContext context;

    public CredentialSelectionAdapter(OperatorCredentialContext context) {
        this.context = context;
    }

    @Override
    public OutboundCredential selectFor(DomainTarget domain) {
        return switch (domain) {
            case GAP -> new OutboundCredential.OperatorToken(context.getOperatorToken());
            case WMS, SCM, FINANCE, ERP ->
                    new OutboundCredential.GapOidcAccessToken(context.getGapOidcAccessToken());
        };
    }
}
