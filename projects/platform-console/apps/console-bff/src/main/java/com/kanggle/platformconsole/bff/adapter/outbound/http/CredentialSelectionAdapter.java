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
 *   GAP                              → OperatorToken(operatorToken)
 *   WMS, SCM, FINANCE, ERP, ECOMMERCE → IamOidcAccessToken(gapOidcAccessToken)
 * </pre>
 *
 * <p>NO fallback path. If the required token is absent, the record constructor
 * in {@link OutboundCredential} throws {@code MissingCredentialException} immediately.
 * The switch is exhaustive (6 domains, no default arm) — adding a 7th domain without
 * updating this switch causes a compile error.
 *
 * <p><b>ECOMMERCE (TASK-MONO-241)</b>: the {@code ECOMMERCE} arm exists so this
 * sealed switch stays exhaustive after the enum's 6th member was added for the
 * § 2.4.9.2 health leg. The Domain Health route is credential-LESS and never
 * invokes this selector; the arm is dormant until the deferred ecommerce
 * Operator Overview snapshot leg (facet a-후속-2) actually fires.
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
            case IAM -> new OutboundCredential.OperatorToken(context.getOperatorToken());
            case WMS, SCM, FINANCE, ERP, ECOMMERCE ->
                    new OutboundCredential.IamOidcAccessToken(context.getIamOidcAccessToken());
        };
    }
}
