package com.example.gateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.gateway.config.OAuth2ResourceServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ecommerce's tenant gate, asserted from the production wiring (TASK-MONO-388).
 *
 * <p>Until now this suite asserted that <em>any</em> well-formed {@code tenant_id} passed —
 * it had a test literally called {@code formerlyCrossTenantWmsNowPasses}, pinning the property
 * that a <strong>wms</strong> token opens the <strong>ecommerce</strong> edge. That was the hole
 * TASK-BE-506 pointed at, written down as a guarantee.
 *
 * <p>The gate now asks what every other edge asks: is this tenant <em>entitled to this domain</em>?
 * The three admitted populations still get in; a tenant with no ecommerce entitlement does not.
 */
@DisplayName("TenantClaimValidator 단위 테스트 (ecommerce gateway — entitlement-trust)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: change the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-356).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/oauth2/jwks", "http://iam.local,iam", "ecommerce").tenantGate();

    // ---------------------------------------------------------------- admitted

    @Test
    @DisplayName("쇼핑객 (tenant_id=ecommerce) → 통과. entitled_domains 없이도.")
    void shopperPasses() {
        // Shoppers are consumers, not subscribers (ADR-MONO-030 § D4-A): they carry no
        // entitled_domains and never will. They pass on the exact tenant_id match — the FIRST
        // branch, not the entitlement branch. Confusing the two leads to the wrong fix
        // ("issue shoppers an entitled_domains claim"), so it is asserted explicitly here.
        assertThat(accepts("ecommerce", null)).isTrue();
    }

    @Test
    @DisplayName("고객 테넌트 운영자 (tenant_id=acme-corp, entitled_domains ∋ ecommerce) → 통과")
    void entitledCustomerTenantOperatorPasses() {
        // The marketplace property (ADR-MONO-030 § D1-A): each customer-tenant runs its own
        // store, so its operator's token names THEIR tenant, not this gateway's. This is the row
        // a naive "just delete the flag" would have broken — and the unit suite would have stayed
        // green, because it only ever tested shoppers.
        assertThat(accepts("acme-corp", List.of("ecommerce"))).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMIN (tenant_id=*) → 통과")
    void superAdminWildcardPasses() {
        // Previously admitted only because "*" happens to be well-formed. Now it is admitted
        // because the gate says so.
        assertThat(accepts("*", null)).isTrue();
    }

    // ---------------------------------------------------------------- refused

    @Test
    @DisplayName("🔴 미구독 테넌트 (tenant_id=wms, ecommerce 미구독) → 거부. 이 한 줄이 이 task 의 전부다.")
    void unsubscribedTenantIsRefused() {
        // The behaviour this task exists to change. Before TASK-MONO-388 this passed, and a test
        // named formerlyCrossTenantWmsNowPasses asserted that it did.
        assertThat(accepts("wms", null)).isFalse();
        assertThat(accepts("globex", null)).isFalse();
        // Entitled to SOMETHING, just not to us. This is precisely BE-506's hole: IAM issues a
        // tenant_id only for an entitled tenant, so "well-formed" felt like "entitled" — but
        // entitled to WHICH domain was never asked.
        assertThat(accepts("fan-platform", List.of("fan-platform", "wms"))).isFalse();
    }

    @Test
    @DisplayName("tenant_id 미존재 → 거부")
    void missingTenantRejected() {
        assertThat(accepts(null, null)).isFalse();
    }

    @Test
    @DisplayName("tenant_id=blank → 거부")
    void blankTenantRejected() {
        assertThat(accepts("   ", null)).isFalse();
    }

    @Test
    @DisplayName("tenant_id 가 문자열이 아님 → 거부")
    void nonStringTenantRejected() {
        assertThat(accepts(42, null)).isFalse();
    }

    // ---------------------------------------------------------------- the fleet semantic, named

    @Test
    @DisplayName("⚠️ tenant_id 없이 entitled_domains 만 있는 토큰 → 통과 (함대 의미론, MONO-383 § 1.9)")
    void entitledWithoutTenantIdPasses_theFleetSemantic() {
        // A SECOND behaviour change, and it widens rather than narrows — so it is asserted out
        // loud instead of being absorbed silently.
        //
        // The entitlement branch in TenantClaimValidator does not require a well-formed
        // tenant_id, so adopting trustEntitledDomains brings ecommerce this semantic. Before
        // this task, ecommerce refused such a token ("any tenant is not no tenant"); erp,
        // finance, scm and wms have always accepted it, and finance's suite pins it.
        //
        // Why it is acceptable rather than merely inherited: IAM's issuance is fail-closed —
        // entitled_domains is populated only after tenant_id is fixed, so a token with the one
        // and not the other is not issued. TASK-MONO-383 § 1.9 examined this exact branch and
        // ruled it a fleet-wide question, to be answered (if ever) by an ADR across all five
        // domains — not by one domain quietly deviating, which is the divergence ADR-MONO-049
        // exists to end.
        assertThat(accepts(null, List.of("ecommerce"))).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private boolean accepts(Object tenantId, List<String> entitledDomains) {
        OAuth2TokenValidatorResult result = validator.validate(jwt(tenantId, entitledDomains));
        return !result.hasErrors();
    }

    private static Jwt jwt(Object tenantId, List<String> entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            b.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            b.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return b.build();
    }
}
