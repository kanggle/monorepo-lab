package com.example.erp.readmodel.config;

import com.example.common.page.PageResult;
import com.example.erp.readmodel.adapter.inbound.web.EmployeeOrgViewController;
import com.example.erp.readmodel.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.erp.readmodel.application.QueryEmployeeOrgViewUseCase;
import com.example.erp.readmodel.presentation.security.OrgScope;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-058 § D4 for read-model-service — the assembled chain's verdicts, exercised end to end
 * with a really RSA-signed JWT against a JWKS this test actually serves.
 *
 * <p>read-model-service has no {@code ActorContext} and no actor converter: it authorises off
 * {@code ReadAuthorizationGate} reading the raw JWT (the read/write asymmetry TASK-ERP-BE-029
 * documented). § D4 changes only how the decoder/enforcer chain is <em>assembled</em>, so
 * {@link #principalIsAPlainJwtNotAnActor()} pins that the adoption did not introduce a principal
 * type as a side effect — the § D1 half of TASK-ERP-BE-037 deliberately does not reach this service.
 *
 * <p>The suite drives the real {@link SecurityConfig} chain (assembled by
 * {@code ResourceServerChainAssembler}) and the real {@link ServiceLevelOAuth2Config} decoder +
 * {@code TenantClaimEnforcer} filter. {@link #unlistedPathWithValidTokenIs403()} pins the measured
 * {@code anyRequest().denyAll()} tail; it turns 404 if the chain is ever assembled with
 * {@code anyRequestAuthenticated()} instead.
 */
@WebMvcTest(controllers = EmployeeOrgViewController.class)
@Import({SecurityConfig.class, ServiceLevelOAuth2Config.class, GlobalExceptionHandler.class})
@DisplayName("read-model-service — the assembled resource-server chain (ADR-MONO-058 D4)")
class SecurityChainAssemblySliceTest {

    private static final String ISSUER = "http://test-issuer";
    private static final String EMPLOYEES = "/api/erp/read-model/employees";

    private static final RSAKey RSA_KEY;
    @SuppressWarnings("resource")
    private static final MockWebServer JWKS = new MockWebServer();

    static {
        try {
            RSA_KEY = new RSAKeyGenerator(2048).keyID("erp-be-037-readmodel").generate();
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
            throw new IllegalStateException("chain-assembly slice fixture failed to start", e);
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
    QueryEmployeeOrgViewUseCase useCase;
    @MockitoBean
    ReadAuthorizationGate readGate;

    /** The live SecurityContext authentication at controller-invocation time. */
    private final AtomicReference<Authentication> liveAuthentication = new AtomicReference<>();

    @BeforeEach
    void captureOnInvocation() {
        lenient().when(readGate.orgScope(any())).thenReturn(OrgScope.platform());
        lenient().when(useCase.list(any(), nullable(String.class), nullable(List.class),
                        anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    liveAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
                    return new PageResult<>(List.of(), 0, 20, 0L, 0);
                });
    }

    private static String bearer(Map<String, Object> claims) {
        return bearer("erp", claims);
    }

    private static String bearer(String tenantId, Map<String, Object> claims) {
        try {
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject("reader-1")
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

    @Test
    @DisplayName("a valid same-tenant token reaches the controller")
    void validTokenAdmitted() throws Exception {
        mockMvc.perform(get(EMPLOYEES).header("Authorization", bearer(Map.of("scope", "erp.read"))))
                .andExpect(status().isOk());

        assertThat(liveAuthentication.get()).isNotNull();
    }

    @Test
    @DisplayName("the principal is a plain Jwt — the D4 adoption introduced no actor type here")
    void principalIsAPlainJwtNotAnActor() throws Exception {
        mockMvc.perform(get(EMPLOYEES).header("Authorization", bearer(Map.of("scope", "erp.read"))))
                .andExpect(status().isOk());

        assertThat(liveAuthentication.get()).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(liveAuthentication.get().getPrincipal()).isInstanceOf(Jwt.class);
        assertThat(((Jwt) liveAuthentication.get().getPrincipal()).getSubject()).isEqualTo("reader-1");
    }

    @Test
    @DisplayName("no bearer token -> 401 UNAUTHORIZED")
    void noTokenIs401() throws Exception {
        mockMvc.perform(get(EMPLOYEES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        assertThat(liveAuthentication.get()).isNull();
    }

    @Test
    @DisplayName("cross-tenant token -> 403 TENANT_FORBIDDEN, the controller is never reached")
    void crossTenantIs403() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", bearer("wms", Map.of("scope", "erp.read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));

        assertThat(liveAuthentication.get()).isNull();
    }

    @Test
    @DisplayName("tenant_id=* -> admitted (allowSuperAdminWildcard survives the D4 adoption)")
    void wildcardTenantAdmitted() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", bearer("*", Map.of("scope", "erp.read"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("entitlement-trust dual-accept: tenant_id=wms + entitled_domains=[erp] -> admitted")
    void entitledCrossTenantAdmitted() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", bearer("wms", Map.of(
                                "scope", "erp.read", "entitled_domains", List.of("erp")))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("entitled somewhere else -> 403 TENANT_FORBIDDEN")
    void entitledElsewhereIs403() throws Exception {
        mockMvc.perform(get(EMPLOYEES)
                        .header("Authorization", bearer("wms", Map.of(
                                "scope", "erp.read", "entitled_domains", List.of("scm")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TENANT_FORBIDDEN"));
    }

    @Test
    @DisplayName("a public path is permitted without any token (PublicPaths.AS_SET feeds the chain)")
    void publicPathNeedsNoToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unlisted path with a VALID token -> 403, not 404: the tail is denyAll()")
    void unlistedPathWithValidTokenIs403() throws Exception {
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
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("reader-1")
                .issuer("http://evil")
                .claim("tenant_id", "erp")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(RSA_KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(RSA_KEY));

        mockMvc.perform(get(EMPLOYEES).header("Authorization", "Bearer " + jwt.serialize()))
                .andExpect(status().isUnauthorized());
    }
}
