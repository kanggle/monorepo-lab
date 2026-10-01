package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import com.example.security.password.Argon2idPasswordHasher;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-615 AC-7 — «before» control for {@code TASK-MONO-745}, through the real browser path.
 *
 * <p>A seller is today a <b>site</b> account in {@code ecommerce}: credential in {@code ecommerce},
 * stored {@code account_roles(ecommerce, acct) = [SELLER]} (product-service provisions it so). Login
 * through the storefront client → code → token. The token's {@code roles} is {@code ["SELLER"]}
 * and does NOT contain {@code CUSTOMER}: the issuance path emits stored roles verbatim and never
 * unions the seed. web-store's {@code signInCallback} then refuses the token
 * ({@code account_type_mismatch}) — the seller cannot shop with that account.
 *
 * <p>account-service is stubbed (WireMock) with the stored {@code [SELLER]}; what this measures is
 * auth-service's half — «stored replaces seed» on the storefront client. Written BEFORE TASK-BE-615
 * changed issuance and expected to stay green after it: sellers are not pool accounts until 745,
 * so the pool «seed ∪ site roles» rule must not reach them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SellerStoreTokenRolesBaselineIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer accountService;

    private static final String EMAIL = "seller-baseline-615@example.com";
    private static final String PASSWORD = "SellerBaselinePassw0rd!";
    private static final String SELLER_ACCOUNT_ID = "0199de70-0000-7000-8000-00000005e615";

    // V0012 confidential storefront client (client_secret_basic + PKCE); dev secret pinned by
    // BcryptHashPinTest; callback path per V0024 — same constants as AccountLockedSessionRevocationIntegrationTest.
    private static final String STORE_CLIENT_ID = "ecommerce-web-store-client";
    private static final String STORE_CLIENT_SECRET = "ecommerce-dev";
    private static final String STORE_REDIRECT_URI = "http://localhost:3000/api/auth/callback/iam";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        accountService = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        accountService.start();
        registry.add("auth.account-service.base-url", accountService::baseUrl);
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/accounts/" + SELLER_ACCOUNT_ID + "/status"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "status": "ACTIVE",
                                  "statusChangedAt": "2026-01-01T00:00:00Z" }
                                """.formatted(SELLER_ACCOUNT_ID))));
        // The seller's stored account_roles in ecommerce — what AccountServiceSellerProvisioner writes.
        accountService.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                        "/internal/tenants/ecommerce/accounts/" + SELLER_ACCOUNT_ID + "/roles"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                { "accountId": "%s", "tenantId": "ecommerce", "roles": ["SELLER"] }
                                """.formatted(SELLER_ACCOUNT_ID))));
    }

    @AfterAll
    static void stopAccountService() {
        if (accountService != null && accountService.isRunning()) {
            accountService.stop();
        }
    }

    @MockitoBean
    IamClientCredentialsTokenProvider gapTokenProvider;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @BeforeEach
    void seedSellerSiteCredential() {
        Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
        accountService.resetRequests();
        credentialJpaRepository.deleteAll();
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(Credential.create(
                SELLER_ACCOUNT_ID, "ecommerce", EMAIL,
                CredentialHash.argon2id(new Argon2idPasswordHasher().hash(PASSWORD)), Instant.now())));
    }

    @Test
    @DisplayName("AC-7: 셀러 사이트 계정 → 스토어 client 폼 로그인 → 토큰 roles == [SELLER] (CUSTOMER 없음 — 저장 역할이 시드를 대체)")
    void sellerOnStore_tokenRolesAreSellerOnly() throws Exception {
        Pkce pkce = Pkce.create();
        MvcResult start = mockMvc.perform(authorize(null, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(start.getResponse().getHeader("Location")).endsWith("/login");
        MockHttpSession saved = (MockHttpSession) start.getRequest().getSession(false);

        MvcResult login = mockMvc.perform(post("/login")
                        .session(saved)
                        .with(csrf())
                        .param("username", EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(login.getResponse().getRedirectedUrl()).contains("/oauth2/authorize");
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        MvcResult resumed = mockMvc.perform(authorize(session, pkce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = resumed.getResponse().getHeader("Location");
        assertThat(location).startsWith(STORE_REDIRECT_URI).contains("code=");
        String code = location.substring(location.indexOf("code=") + 5).split("&")[0];

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                                (STORE_CLIENT_ID + ":" + STORE_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)))
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", STORE_REDIRECT_URI)
                        .param("code_verifier", pkce.verifier()))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(token.getResponse().getContentAsString())
                .get("access_token").asText();

        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        List<String> roles = new ArrayList<>();
        payload.get("roles").forEach(r -> roles.add(r.asText()));
        assertThat(roles)
                .as("stored [SELLER] replaces the [CUSTOMER] seed — the seller cannot shop (745 «before»)")
                .containsExactly("SELLER");
        assertThat(payload.get("tenant_id").asText()).isEqualTo("ecommerce");
        assertThat(payload.get("sub").asText()).isEqualTo(SELLER_ACCOUNT_ID);
    }

    private record Pkce(String verifier, String challenge) {
        static Pkce create() throws Exception {
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    UUID.randomUUID().toString().replace("-", "").getBytes(StandardCharsets.UTF_8));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            return new Pkce(verifier, challenge);
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(
            MockHttpSession session, Pkce pkce) {
        var builder = get("/oauth2/authorize")
                .accept(MediaType.TEXT_HTML)
                .queryParam("response_type", "code")
                .queryParam("client_id", STORE_CLIENT_ID)
                .queryParam("redirect_uri", STORE_REDIRECT_URI)
                .queryParam("scope", "openid profile email")
                .queryParam("code_challenge", pkce.challenge())
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", "be-615-ac7");
        return session != null ? builder.session(session) : builder;
    }
}
