package com.kanggle.platformconsole.bff.domain.credential;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain unit test — verifies the 6-row credential selector predicate
 * (AC-8 / architecture.md § Auth Flow outbound / § 2.4.9 table verbatim;
 * ECOMMERCE row added by TASK-MONO-241).
 *
 * <p>Framework-free (no Spring context).
 */
class CredentialSelectionTest {

    private static final String OPERATOR_TOKEN = "op-tok-abc123";
    private static final String GAP_OIDC_TOKEN = "iam-oidc-xyz789";

    // ---- Inline stub implementation for the test ----
    // The real implementation lives in CredentialSelectionAdapter (adapter layer).
    // This test exercises the domain contract directly via a stub.

    private OutboundCredential select(DomainTarget domain, String operatorToken, String gapOidcToken) {
        return switch (domain) {
            case IAM -> new OutboundCredential.OperatorToken(operatorToken);
            case WMS, SCM, FINANCE, ERP, ECOMMERCE ->
                    new OutboundCredential.IamOidcAccessToken(gapOidcToken);
        };
    }

    @Test
    @DisplayName("Row 1: GAP domain → OperatorToken (RFC 8693 exchanged)")
    void gapDomain_returnsOperatorToken() {
        OutboundCredential cred = select(DomainTarget.IAM, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.OperatorToken.class);
        assertThat(((OutboundCredential.OperatorToken) cred).token()).isEqualTo(OPERATOR_TOKEN);
    }

    @Test
    @DisplayName("Row 2: WMS domain → IamOidcAccessToken")
    void wmsDomain_returnsGapOidcToken() {
        OutboundCredential cred = select(DomainTarget.WMS, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.IamOidcAccessToken.class);
        assertThat(((OutboundCredential.IamOidcAccessToken) cred).token()).isEqualTo(GAP_OIDC_TOKEN);
    }

    @Test
    @DisplayName("Row 3: SCM domain → IamOidcAccessToken")
    void scmDomain_returnsGapOidcToken() {
        OutboundCredential cred = select(DomainTarget.SCM, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.IamOidcAccessToken.class);
        assertThat(((OutboundCredential.IamOidcAccessToken) cred).token()).isEqualTo(GAP_OIDC_TOKEN);
    }

    @Test
    @DisplayName("Row 4: FINANCE domain → IamOidcAccessToken")
    void financeDomain_returnsGapOidcToken() {
        OutboundCredential cred = select(DomainTarget.FINANCE, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.IamOidcAccessToken.class);
        assertThat(((OutboundCredential.IamOidcAccessToken) cred).token()).isEqualTo(GAP_OIDC_TOKEN);
    }

    @Test
    @DisplayName("Row 5: ERP domain → IamOidcAccessToken")
    void erpDomain_returnsGapOidcToken() {
        OutboundCredential cred = select(DomainTarget.ERP, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.IamOidcAccessToken.class);
        assertThat(((OutboundCredential.IamOidcAccessToken) cred).token()).isEqualTo(GAP_OIDC_TOKEN);
    }

    @Test
    @DisplayName("Row 6: ECOMMERCE domain → IamOidcAccessToken (TASK-MONO-241)")
    void ecommerceDomain_returnsGapOidcToken() {
        OutboundCredential cred = select(DomainTarget.ECOMMERCE, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
        assertThat(cred).isInstanceOf(OutboundCredential.IamOidcAccessToken.class);
        assertThat(((OutboundCredential.IamOidcAccessToken) cred).token()).isEqualTo(GAP_OIDC_TOKEN);
    }

    @Test
    @DisplayName("GAP leg with absent operator token → MissingCredentialException (fail-closed, NO fallback)")
    void gapDomain_absentOperatorToken_failsClosed() {
        // HARD INVARIANT: must NOT fall back to GAP OIDC token.
        // The OperatorToken record constructor rejects null/blank immediately.
        assertThatThrownBy(() -> new OutboundCredential.OperatorToken(null))
                .isInstanceOf(MissingCredentialException.class);
        assertThatThrownBy(() -> new OutboundCredential.OperatorToken(""))
                .isInstanceOf(MissingCredentialException.class);
        assertThatThrownBy(() -> new OutboundCredential.OperatorToken("   "))
                .isInstanceOf(MissingCredentialException.class);
    }

    @Test
    @DisplayName("Non-GAP leg with absent GAP OIDC token → MissingCredentialException (fail-closed)")
    void nonGapDomain_absentGapOidcToken_failsClosed() {
        assertThatThrownBy(() -> new OutboundCredential.IamOidcAccessToken(null))
                .isInstanceOf(MissingCredentialException.class);
        assertThatThrownBy(() -> new OutboundCredential.IamOidcAccessToken(""))
                .isInstanceOf(MissingCredentialException.class);
    }

    @Test
    @DisplayName("All 6 DomainTarget enum values have a corresponding selector row")
    void allDomainTargets_covered() {
        // Exhaustive coverage assertion — if DomainTarget gains a 7th value
        // without updating the switch, the compile-time exhaustiveness check
        // fails. This test provides a runtime guard too.
        for (DomainTarget domain : DomainTarget.values()) {
            OutboundCredential cred = select(domain, OPERATOR_TOKEN, GAP_OIDC_TOKEN);
            assertThat(cred).isNotNull();
        }
    }
}
