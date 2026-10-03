package com.example.product.presentation.controller;

import com.example.product.TestProductServiceApplication;
import com.example.product.application.dto.SellerSummary;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.model.SellerStatus;
import com.example.product.domain.tenant.TenantContext;
import com.example.product.infrastructure.config.ProductSecurityConfig;
import com.example.product.presentation.advice.GlobalExceptionHandler;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-759 — product-service's first JWT surface, driven through the REAL
 * {@link ProductSecurityConfig} (both chains), the REAL decoder (JWKS fetched over HTTP from a
 * WireMock stand-in) and the REAL validator chain. Only the service below the controller is a
 * mock.
 *
 * <p>The cells that matter are the refusals: a token that is valid in every other respect but
 * lacks the scope, or was assumed for another tenant, must not read a seller; and the read
 * credential must not reach any write (AC-3).
 */
@WebMvcTest(controllers = {InternalSellerController.class, AdminSellerController.class})
@ContextConfiguration(classes = TestProductServiceApplication.class)
@Import({GlobalExceptionHandler.class, ProductSecurityConfig.class})
@DisplayName("product-service /internal/** chain (TASK-MONO-759)")
class InternalSellerChainTest {

    private static final String ISSUER = "http://iam.test";
    private static final RSAKey KEY;
    private static final RSAKey FOREIGN_KEY;
    private static final WireMockServer JWKS = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("k1").generate();
            FOREIGN_KEY = new RSAKeyGenerator(2048).keyID("k1").generate();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        JWKS.start();
        JWKS.stubFor(get(urlEqualTo("/oauth2/jwks")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody(new JWKSet(KEY.toPublicJWK()).toString())));
    }

    // The JWKS server is not stopped per class: the Spring context is cached across the nested
    // classes and keeps its URL; the JVM exit tears the server down.

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("product.internal.oauth2.jwk-set-uri", () -> JWKS.baseUrl() + "/oauth2/jwks");
        r.add("product.internal.oauth2.issuer", () -> ISSUER);
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SellerQueryService sellerQueryService;

    @MockitoBean
    RegisterSellerService registerSellerService;

    // ------------------------------------------------------------------ token factory

    private static String token(RSAKey key, String issuer, String tenant, List<String> scope,
                                List<String> roles) {
        try {
            JWTClaimsSet.Builder c = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject("artist-service-client")
                    .audience("artist-service-client")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .jwtID(UUID.randomUUID().toString());
            if (tenant != null) c.claim("tenant_id", tenant);
            if (scope != null) c.claim("scope", scope);
            if (roles != null) c.claim("roles", roles);
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(), c.build());
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sellerReadToken() {
        return token(KEY, ISSUER, "ecommerce", List.of("store.seller.read"), null);
    }

    private static SellerSummary seller(String id, SellerStatus status) {
        Instant t = Instant.parse("2026-10-03T00:00:00Z");
        return new SellerSummary(id, "Seller " + id, status, t, t);
    }

    // ------------------------------------------------------------------ the read

    @Nested
    @DisplayName("GET /internal/sellers/{id} with the seller-read workload token")
    class Read {

        @Test
        @DisplayName("200 → {sellerId, status} only, looked up in the TOKEN's tenant even when the header names another")
        void readsTheSellerInTheTokensTenant() throws Exception {
            AtomicReference<String> tenantAtLookup = new AtomicReference<>();
            given(sellerQueryService.getSeller("s-1")).willAnswer(inv -> {
                tenantAtLookup.set(TenantContext.currentTenant());
                return seller("s-1", SellerStatus.SUSPENDED);
            });

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/internal/sellers/s-1")
                            .header("Authorization", "Bearer " + sellerReadToken())
                            .header("X-Tenant-Id", "demo-corp"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sellerId").value("s-1"))
                    .andExpect(jsonPath("$.status").value("SUSPENDED"))
                    .andExpect(jsonPath("$.displayName").doesNotExist());

            assertThat(tenantAtLookup.get())
                    .as("a forged X-Tenant-Id must not move the lookup off the token's tenant")
                    .isEqualTo("ecommerce");
        }

        @Test
        @DisplayName("404 SELLER_NOT_FOUND — the definite «no such seller» the fan caller maps to empty")
        void unknownSellerIs404() throws Exception {
            given(sellerQueryService.getSeller("ghost")).willThrow(new SellerNotFoundException("ghost"));

            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/internal/sellers/ghost")
                            .header("Authorization", "Bearer " + sellerReadToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SELLER_NOT_FOUND"));
        }
    }

    // ------------------------------------------------------------------ refusals (401)

    @Nested
    @DisplayName("401 — every token that is not the seller-read workload token for ecommerce")
    class Refused {

        private void expect401(String bearer) throws Exception {
            var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/internal/sellers/s-1");
            if (bearer != null) req.header("Authorization", "Bearer " + bearer);
            mvc.perform(req)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            verify(sellerQueryService, never()).getSeller(anyString());
        }

        @Test
        @DisplayName("no token")
        void noToken() throws Exception {
            expect401(null);
        }

        @Test
        @DisplayName("🔴 a shopper token (CUSTOMER role, no store.seller.read) — signature + issuer alone must not admit it")
        void customerTokenWithoutTheScope() throws Exception {
            expect401(token(KEY, ISSUER, "ecommerce", List.of("openid", "profile"), List.of("CUSTOMER")));
        }

        @Test
        @DisplayName("🔴 the right scope assumed for ANOTHER tenant")
        void rightScopeWrongTenant() throws Exception {
            expect401(token(KEY, ISSUER, "demo-corp", List.of("store.seller.read"), null));
        }

        @Test
        @DisplayName("🔴 the right scope, the SUPER_ADMIN wildcard tenant — no wildcard on this surface")
        void wildcardTenant() throws Exception {
            expect401(token(KEY, ISSUER, "*", List.of("store.seller.read"), null));
        }

        @Test
        @DisplayName("the right scope and its own fan tenant (an un-exchanged token)")
        void unexchangedFanToken() throws Exception {
            expect401(token(KEY, ISSUER, "fan-platform", List.of("store.seller.read"), null));
        }

        @Test
        @DisplayName("wrong issuer")
        void wrongIssuer() throws Exception {
            expect401(token(KEY, "http://evil.test", "ecommerce", List.of("store.seller.read"), null));
        }

        @Test
        @DisplayName("signed by a key the JWKS does not publish")
        void foreignKey() throws Exception {
            expect401(token(FOREIGN_KEY, ISSUER, "ecommerce", List.of("store.seller.read"), null));
        }
    }

    // ------------------------------------------------------------------ AC-3: read cannot write

    @Nested
    @DisplayName("🔴 AC-3 — the seller-read token cannot change a seller")
    class CannotWrite {

        @Test
        @DisplayName("PATCH / POST / DELETE under /internal/sellers → 403, nothing called")
        void noWriteVerbUnderInternal() throws Exception {
            String t = sellerReadToken();
            mvc.perform(patch("/internal/sellers/s-1").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/internal/sellers/s-1/close").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/internal/sellers/s-1").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/internal/anything-else").header("Authorization", "Bearer " + t))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(sellerQueryService, registerSellerService);
        }

        @Test
        @DisplayName("the operator write path still gates on X-User-Role — the token alone opens nothing there")
        void operatorWritePathIgnoresTheToken() throws Exception {
            mvc.perform(post("/api/admin/sellers/s-1/close")
                            .header("Authorization", "Bearer " + sellerReadToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            verify(registerSellerService, never()).close(anyString());
        }
    }

    // ------------------------------------------------------------------ header trust unchanged

    @Test
    @DisplayName("🔵 the header-trust surface is unchanged — an operator header still works with NO token")
    void headerTrustPathUnchanged() throws Exception {
        given(sellerQueryService.getSeller("s-1")).willReturn(seller("s-1", SellerStatus.ACTIVE));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/sellers/s-1")
                        .header("X-User-Role", "ECOMMERCE_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value("s-1"));
    }
}
