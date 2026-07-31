package com.example.erp.masterdata.infrastructure.security;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.presentation.advice.GlobalExceptionHandler;
import com.example.erp.masterdata.presentation.controller.DepartmentController;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D1 + § D4 for masterdata-service — the auth path, exercised end to end.
 *
 * <p>Everything here goes through the <strong>real production wiring</strong>: the real
 * {@link SecurityConfig} chain (assembled by {@code ResourceServerChainAssembler}), the real
 * {@link ServiceLevelOAuth2Config} decoder + {@code TenantClaimEnforcer} filter, and a really
 * RSA-signed JWT verified against a JWKS this test actually serves. Nothing is hand-built: a test
 * that constructed a {@code Jwt} or an {@code ActorContext} directly would prove the classes in
 * isolation and stay green while the wired chain changed underneath it.
 *
 * <p>The four assertions this suite exists for, and what each one notices:
 *
 * <ul>
 *   <li><strong>claim-lifting parity</strong> — both wire forms of the role claim, plus the
 *       {@code scope}/{@code scopes} aliases erp folds into the same set. Dropping the aliases
 *       (i.e. adopting the shared {@code ActorClaims.from} normalisation verbatim) turns
 *       {@link #scopeClaimIsAnErpRoleToken()} and {@link #rolesAndScopeAreUnioned()} red.</li>
 *   <li><strong>the two erp-only components</strong> — {@code dataScopeDepartmentIds} and
 *       {@code entitledDomains} still arrive on the actor.</li>
 *   <li><strong>the verdicts</strong> — 401 with no bearer, 403 {@code TENANT_FORBIDDEN}
 *       cross-tenant, wildcard admit, entitlement dual-accept admit.</li>
 *   <li><strong>the {@code anyRequest()} tail</strong> — {@link #unlistedPathWithValidTokenIs403()}
 *       is the measured {@code denyAll()} posture; it turns 404 the moment someone swaps
 *       {@code anyRequestDenied()} for {@code anyRequestAuthenticated()}.</li>
 * </ul>
 */
@WebMvcTest(controllers = DepartmentController.class)
@Import({SecurityConfig.class, ServiceLevelOAuth2Config.class, GlobalExceptionHandler.class})
@DisplayName("masterdata-service — actor claims + assembled chain, through the real filter chain")
class ActorContextAuthPathSliceTest {

    private static final String ISSUER = "http://test-issuer";
    private static final String DEPARTMENTS = "/api/erp/masterdata/departments";

    private static final RSAKey RSA_KEY;
    @SuppressWarnings("resource")
    private static final MockWebServer JWKS = new MockWebServer();

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("erp-be-037-masterdata").generate();
            String jwks = "{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}";
            JWKS.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody(jwks);
                }
            });
            JWKS.start();
        } catch (Exception e) {
            throw new IllegalStateException("auth-path slice fixture failed to start", e);
        }
    }

    @DynamicPropertySource
    static void oauthProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("erpplatform.oauth2.allowed-issuers", () -> ISSUER);
        registry.add("erpplatform.oauth2.required-tenant-id", () -> "erp");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MasterdataApplicationService service;
    @MockitoBean
    IdempotentExecution idempotency;

    /** The actor the controller actually handed the application service. */
    private final AtomicReference<ActorContext> boundActor = new AtomicReference<>();

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        when(service.listDepartments(any(), any(), anyInt(), anyInt())).thenAnswer(invocation -> {
            boundActor.set(invocation.getArgument(0));
            liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return new PageResult<>(List.of(), 0L);
        });
    }

    private List<String> capturedAuthorities() {
        return liveAuthentication.get().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static String bearer(Map<String, Object> claims) {
        return bearer("erp", claims);
    }

    private static String bearer(String tenantId, Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject("op-user-1")
                    .issuer(ISSUER)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)));
            if (tenantId != null) {
                builder.claim("tenant_id", tenantId);
            }
            claims.forEach(builder::claim);
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    builder.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return "Bearer " + jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign the test token", e);
        }
    }

    // -----------------------------------------------------------------------
    // D1 — claim lifting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("array-form roles claim -> ROLE_-prefixed authorities + bound actor")
    void arrayFormRolesClaim() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("roles", List.of("ERP_OPERATOR", "ERP_ADMIN")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().actorId()).isEqualTo("op-user-1");
        assertThat(boundActor.get().tenantId()).isEqualTo("erp");
        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("ERP_OPERATOR", "ERP_ADMIN");
        assertThat(boundActor.get().isOperator()).isTrue();
        assertThat(capturedAuthorities())
                .containsExactlyInAnyOrder("ROLE_ERP_OPERATOR", "ROLE_ERP_ADMIN");
        assertThat(liveAuthentication.get().getName()).isEqualTo("op-user-1");
    }

    @Test
    @DisplayName("space-delimited role claim (no roles claim) -> the same authorities")
    void spaceDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("role", "ERP_OPERATOR ERP_ADMIN"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("ERP_OPERATOR", "ERP_ADMIN");
        assertThat(capturedAuthorities())
                .containsExactlyInAnyOrder("ROLE_ERP_OPERATOR", "ROLE_ERP_ADMIN");
    }

    @Test
    @DisplayName("comma-delimited role claim -> the same authorities")
    void commaDelimitedStringRoleClaim() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("role", "ERP_OPERATOR,ERP_ADMIN"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("ERP_OPERATOR", "ERP_ADMIN");
    }

    @Test
    @DisplayName("the OAuth2 scope claim is an erp role token — hasScope + a ROLE_ authority")
    void scopeClaimIsAnErpRoleToken() throws Exception {
        // erp's alias set is WIDER than the fleet mechanism's roles-or-role normalisation:
        // RoleScopeAuthorizationAdapter authorises READ/WRITE off erp.read / erp.write, which a
        // GAP client_credentials token delivers on `scope`. If this service ever adopted the
        // shared ActorClaims.from(...) normalisation verbatim, every machine token would arrive
        // with an EMPTY role set and lose its authorization — this is the assertion that notices.
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("scope", "erp.read erp.write"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("erp.read", "erp.write");
        assertThat(boundActor.get().hasScope("erp.read")).isTrue();
        assertThat(boundActor.get().hasScope("erp.write")).isTrue();
        assertThat(capturedAuthorities())
                .containsExactlyInAnyOrder("ROLE_erp.read", "ROLE_erp.write");
    }

    @Test
    @DisplayName("roles and scope are UNIONed, not one-wins — both land in the same set")
    void rolesAndScopeAreUnioned() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of(
                                "roles", List.of("ERP_OPERATOR"),
                                "scope", "erp.read"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().roles()).containsExactlyInAnyOrder("ERP_OPERATOR", "erp.read");
        assertThat(capturedAuthorities())
                .containsExactlyInAnyOrder("ROLE_ERP_OPERATOR", "ROLE_erp.read");
    }

    @Test
    @DisplayName("no role/scope claim at all -> authenticated actor with zero roles and zero authorities")
    void noRoleClaim() throws Exception {
        mockMvc.perform(get(DEPARTMENTS).header("Authorization", bearer(Map.of())))
                .andExpect(status().isOk());

        assertThat(boundActor.get().actorId()).isEqualTo("op-user-1");
        assertThat(boundActor.get().roles()).isEmpty();
        assertThat(capturedAuthorities()).isEmpty();
    }

    @Test
    @DisplayName("erp's two extra components — org_scope + entitled_domains — reach the actor")
    void erpOnlyComponentsAreThreaded() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of(
                                "scope", "erp.read",
                                "org_scope", List.of("dept-sales", "dept-hr"),
                                "entitled_domains", List.of("erp", "scm")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().dataScopeDepartmentIds())
                .containsExactlyInAnyOrder("dept-sales", "dept-hr");
        assertThat(boundActor.get().isPlatformScope()).isFalse();
        assertThat(boundActor.get().entitledDomains()).containsExactlyInAnyOrder("erp", "scm");
        assertThat(boundActor.get().isEntitledTo("erp")).isTrue();
        assertThat(boundActor.get().isEntitledTo("finance")).isFalse();
    }

    @Test
    @DisplayName("org_scope=[\"*\"] -> platform data scope")
    void wildcardOrgScopeIsPlatformScope() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of(
                                "scope", "erp.read", "org_scope", List.of("*")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isPlatformScope()).isTrue();
    }

    @Test
    @DisplayName("no org_scope -> EMPTY data scope, fail-closed (TASK-ERP-BE-029)")
    void absentOrgScopeStaysEmpty() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("scope", "erp.write"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().dataScopeDepartmentIds()).isEmpty();
        assertThat(boundActor.get().isPlatformScope()).isFalse();
    }

    @Test
    @DisplayName("the bound principal is this service's own ActorContext type")
    void boundActorIsTheServicesOwnType() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer(Map.of("scope", "erp.read"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get()).isExactlyInstanceOf(ActorContext.class);
        assertThat(liveAuthentication.get().getPrincipal()).isSameAs(boundActor.get());
    }

    // -----------------------------------------------------------------------
    // D4 — the assembled chain's verdicts
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("no bearer token -> 401 UNAUTHORIZED, the controller is never reached")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get(DEPARTMENTS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer("wms", Map.of("scope", "erp.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(boundActor.get()).isNull();
    }

    @Test
    @DisplayName("tenant_id=* -> admitted (allowSuperAdminWildcard survives the D4 adoption)")
    void wildcardTenantAdmitted() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer("*", Map.of("scope", "erp.read"))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().tenantId()).isEqualTo("*");
    }

    @Test
    @DisplayName("entitlement-trust dual-accept: tenant_id=wms + entitled_domains=[erp] -> admitted")
    void entitledCrossTenantAdmitted() throws Exception {
        // trustEntitledDomains() must survive on BOTH layers — the decode-time validator and the
        // TenantClaimEnforcer filter. Dropping it from either turns this 403.
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer("wms", Map.of(
                                "scope", "erp.read", "entitled_domains", List.of("erp")))))
                .andExpect(status().isOk());

        assertThat(boundActor.get().isEntitledTo("erp")).isTrue();
    }

    @Test
    @DisplayName("entitled somewhere else -> 403 TENANT_FORBIDDEN")
    void entitledElsewhereIs403() throws Exception {
        mockMvc.perform(get(DEPARTMENTS)
                        .header("Authorization", bearer("wms", Map.of(
                                "scope", "erp.read", "entitled_domains", List.of("scm")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("a public path is permitted without any token (PublicPaths.AS_SET feeds the chain)")
    void publicPathNeedsNoToken() throws Exception {
        // The actuator endpoints are not mapped in a @WebMvcTest slice, so a permitted path lands
        // on the DispatcherServlet's 404. That is the point: an UNpermitted path would have been
        // stopped by the chain at 401 long before reaching a handler-mapping decision.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unlisted path with a VALID token -> 403, not 404: the tail is denyAll()")
    void unlistedPathWithValidTokenIs403() throws Exception {
        // Measured posture, pinned. masterdata-service's chain ends anyRequest().denyAll(); if it
        // were ever assembled with anyRequestAuthenticated() instead, this valid-token request to
        // an unmapped path would fall through the chain and 404.
        mockMvc.perform(get("/not-an-erp-api")
                        .header("Authorization", bearer(Map.of("scope", "erp.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("an unlisted path with NO token -> 401")
    void unlistedPathWithoutTokenIs401() throws Exception {
        mockMvc.perform(get("/not-an-erp-api"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("an issuer outside erpplatform.oauth2.allowed-issuers -> 401")
    void unlistedIssuerIs401() throws Exception {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("op-user-1")
                    .issuer("http://evil")
                    .claim("tenant_id", "erp")
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(RSA_KEY));
            mockMvc.perform(get(DEPARTMENTS).header("Authorization", "Bearer " + jwt.serialize()))
                    .andExpect(status().isUnauthorized());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
