package com.example.scmplatform.logistics.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.TenantClaimEnforcer;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * logistics-service's tenant gate policy, pinned — dual-accept ({@code tenant_id ∈ {scm,*}} ∪
 * signed {@code entitled_domains ∋ scm}), fail-closed otherwise. Both subjects come from a real
 * {@link ServiceLevelOAuth2Config}: deleting {@code .allowSuperAdminWildcard()} /
 * {@code .trustEntitledDomains()} / {@code .exempt(...)} from the wiring turns this suite red.
 */
@DisplayName("scm/logistics — the tenant gate's policy, both halves")
class ScmTenantGatePolicyTest {

    private static final String ISSUER = "http://iam.local";
    private static final String API_PATH = "/api/logistics/dispatches/0192dddd-0000-0000-0000-000000000001";

    private final ServiceLevelOAuth2Config config = wiredConfig();
    private final OAuth2TokenValidator<Jwt> validator = config.jwtTokenValidator();
    private final TenantClaimEnforcer enforcer = config.tenantClaimEnforcer();

    private static ServiceLevelOAuth2Config wiredConfig() {
        ServiceLevelOAuth2Config config = new ServiceLevelOAuth2Config();
        ReflectionTestUtils.setField(config, "requiredTenantId", "scm");
        ReflectionTestUtils.setField(config, "allowedIssuersCsv", ISSUER);
        return config;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("the decoder admits")
    class DecoderAdmits {

        @Test
        @DisplayName("tenant_id=scm")
        void exactTenant() {
            assertThat(validator.validate(jwt("scm", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=* — the SUPER_ADMIN platform scope")
        void wildcard() {
            assertThat(validator.validate(jwt("*", null)).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("tenant_id=globex-corp + entitled_domains=[scm, erp]")
        void entitledCrossTenant() {
            assertThat(validator.validate(jwt("globex-corp", List.of("scm", "erp"))).hasErrors())
                    .isFalse();
        }

        @Test
        @DisplayName("entitled_domains=[scm] with NO tenant_id at all")
        void entitledWithoutTenantId() {
            assertThat(validator.validate(jwt(null, List.of("scm"))).hasErrors()).isFalse();
        }
    }

    @Nested
    @DisplayName("the decoder refuses")
    class DecoderRefuses {

        @Test
        @DisplayName("a cross-tenant token — with the tenant_mismatch code")
        void crossTenant() {
            var result = validator.validate(jwt("wms", null));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("a token entitled somewhere ELSE — with the tenant_mismatch code")
        void entitledElsewhere() {
            var result = validator.validate(jwt("acme", List.of("wms")));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("neither tenant_id nor entitled_domains — with the tenant_mismatch code")
        void noTenantNoEntitlement() {
            var result = validator.validate(jwt(null, null));
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors())
                    .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
        }

        @Test
        @DisplayName("an issuer outside the allowlist raises invalid_issuer")
        void unlistedIssuer() {
            Jwt token = Jwt.withTokenValue("t")
                    .header("alg", "RS256")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(60))
                    .claim("iss", "http://evil")
                    .claim("sub", "s")
                    .claim(TenantClaimValidator.CLAIM_TENANT_ID, "scm")
                    .build();
            var result = validator.validate(token);
            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors()).anyMatch(e -> "invalid_issuer".equals(e.getErrorCode()));
        }
    }

    @Nested
    @DisplayName("the filter refuses")
    class FilterRefuses {

        @Test
        @DisplayName("cross-tenant (wms) → 403 TENANT_FORBIDDEN, chain NOT invoked")
        void crossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("wms", null), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
            assertThat(outcome.body).contains(TenantClaimEnforcer.CODE_TENANT_FORBIDDEN);
        }

        @Test
        @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] → 403, chain NOT invoked")
        void nonEntitledCrossTenantBlocked() throws Exception {
            Outcome outcome = filter(jwt("acme", List.of("wms")), API_PATH);
            assertThat(outcome.called).isFalse();
            assertThat(outcome.status).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("the filter admits exactly what the decoder admits — no decode-pass / filter-block split")
    void theTwoLayersAgree() throws Exception {
        List<Jwt> tokens = List.of(
                jwt("scm", null),
                jwt("*", null),
                jwt("acme", List.of("scm")),
                jwt(null, List.of("scm")),
                jwt("wms", null),
                jwt("acme", List.of("wms")),
                jwt(null, null));

        for (Jwt token : tokens) {
            boolean decoderAdmits = !validator.validate(token).hasErrors();
            boolean filterAdmits = filter(token, API_PATH).called;
            assertThat(filterAdmits)
                    .as("decoder and filter disagree on %s", token.getClaims())
                    .isEqualTo(decoderAdmits);
        }
    }

    private Outcome filter(Jwt token, String uri) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();
        enforcer.doFilter(request, response, chain);
        SecurityContextHolder.clearContext();
        return new Outcome(chain.called, response.getStatus(), response.getContentAsString());
    }

    private record Outcome(boolean called, int status, String body) {}

    private static Jwt jwt(String tenantId, List<String> entitledDomains) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("iss", ISSUER)
                .claim("sub", "s");
        if (tenantId != null) {
            builder.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            builder.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return builder.build();
    }

    private static final class RecordingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.called = true;
        }
    }
}
