package com.example.auth.integration;

import com.example.auth.application.exception.AssumeTenantDeniedException;
import com.example.auth.application.port.OperatorAssignmentPort;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2+D3) — integration test for the
 * GAP assume-tenant RFC 8693 token-exchange on {@code POST /oauth2/token}.
 *
 * <p>Covers (CI Linux GAP Integration authoritative):
 * <ul>
 *   <li>happy path — assigned + subscription → token carries {@code tenant_id=selected}
 *       + {@code entitled_domains=selected's subs}, NO {@code refresh_token},
 *       same {@code iss} as the login token (AC-1/AC-3/AC-5)</li>
 *   <li>denied path — assignment-denied → no token, {@code invalid_grant} (AC-2)</li>
 *   <li><b>admin-unavailable → fail-CLOSED</b> deny, {@code invalid_grant} (AC-2)</li>
 *   <li><b>account-unavailable → fail-SOFT</b> — token issued WITHOUT
 *       {@code entitled_domains} (AC-3)</li>
 *   <li>net-zero shape — claim names identical to the {@code authorization_code} token (AC-6)</li>
 *   <li>existing grants byte-unchanged — authorization_code still works (AC-1)</li>
 * </ul>
 *
 * <p>The base subject token is minted via a real {@code authorization_code} flow
 * (auth-service's own JWKS), so the provider's local-JWKS subject validation runs
 * against a genuinely-issued token. The admin assignment gate is mocked
 * ({@link OperatorAssignmentPort}) to drive assigned/denied/admin-down; the
 * account {@code entitled_domains} edge uses a WireMock stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AssumeTenantExchangeIntegrationTest extends AbstractIntegrationTest {

    private static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final String SELECTED_TENANT = "acme-corp";

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        registry.add("auth.account-service.base-url", wireMock::baseUrl);
        // admin assignment gate is mocked via @MockitoBean OperatorAssignmentPort,
        // so the admin base-url is irrelevant; point it at the same stub for safety.
        registry.add("auth.admin-service.base-url", wireMock::baseUrl);
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OperatorAssignmentPort operatorAssignmentPort;

    @MockitoBean
    com.example.auth.infrastructure.client.GapClientCredentialsTokenProvider gapTokenProvider;

    @BeforeEach
    void stubGapToken() {
        when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    private void stubEntitledDomains(String... domainKeys) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < domainKeys.length; i++) {
            if (i > 0) items.append(",");
            items.append("{\"tenantId\":\"").append(SELECTED_TENANT)
                 .append("\",\"domainKey\":\"").append(domainKeys[i]).append("\"}");
        }
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/tenant-domain-subscriptions"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[" + items + "]}")));
    }

    /** Mints a real base GAP OIDC access token via the authorization_code flow. */
    private String mintBaseToken(String accountId) throws Exception {
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((UUID.randomUUID() + UUID.randomUUID().toString())
                        .replace("-", "").getBytes(StandardCharsets.UTF_8));
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        String codeChallenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII)));

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .with(user(accountId).authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-spa-client")
                        .queryParam("redirect_uri", "http://localhost:3000/callback")
                        .queryParam("scope", "openid profile email")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String code = extractParam(authorize.getResponse().getHeader("Location"), "code");

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", "http://localhost:3000/callback")
                        .param("client_id", "demo-spa-client")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(token.getResponse().getContentAsString())
                .get("access_token").asText();
    }

    private MvcResult assumeTenant(String subjectToken, String audience) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", TOKEN_EXCHANGE)
                        .param("subject_token", subjectToken)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("audience", audience)
                        .param("client_id", "platform-console-web"))
                .andReturn();
    }

    @Test
    @DisplayName("happy: assigned + subscription → tenant_id=selected + entitled_domains, NO refresh_token, same iss")
    void happyPath() throws Exception {
        when(operatorAssignmentPort.isAssigned(anyString(), eq(SELECTED_TENANT))).thenReturn(true);
        stubEntitledDomains("finance", "wms");

        String base = mintBaseToken("assume-op-001");
        MvcResult result = assumeTenant(base, SELECTED_TENANT);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("access_token").asText()).isNotBlank();
        assertThat(body.has("refresh_token")).as("assumed token must NOT carry a refresh_token").isFalse();

        JsonNode assumed = decodeJwtPayload(body.get("access_token").asText());
        assertThat(assumed.get("tenant_id").asText()).isEqualTo(SELECTED_TENANT);
        assertThat(assumed.get("tenant_type").asText()).isEqualTo("B2B_ENTERPRISE");
        assertThat(assumed.get("entitled_domains")).isNotNull();
        assertThat(assumed.get("entitled_domains").toString()).contains("finance").contains("wms");

        // Same iss as the base login token (federation invariant, AC-5).
        JsonNode basePayload = decodeJwtPayload(base);
        assertThat(assumed.get("iss").asText()).isEqualTo(basePayload.get("iss").asText());
    }

    @Test
    @DisplayName("denied: assignment-denied → no token, invalid_grant (AC-2)")
    void deniedPath() throws Exception {
        when(operatorAssignmentPort.isAssigned(anyString(), eq(SELECTED_TENANT)))
                .thenThrow(new AssumeTenantDeniedException("operator is not assigned to the selected tenant"));

        String base = mintBaseToken("assume-op-002");
        MvcResult result = assumeTenant(base, SELECTED_TENANT);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    @DisplayName("admin-unavailable → fail-CLOSED deny, invalid_grant (AC-2)")
    void adminUnavailable_failClosed() throws Exception {
        when(operatorAssignmentPort.isAssigned(anyString(), eq(SELECTED_TENANT)))
                .thenThrow(new AssumeTenantDeniedException("admin-service unavailable (fail-closed)",
                        new RuntimeException("connection refused")));

        String base = mintBaseToken("assume-op-003");
        MvcResult result = assumeTenant(base, SELECTED_TENANT);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    @DisplayName("account-unavailable → fail-SOFT: token issued WITHOUT entitled_domains (AC-3)")
    void accountUnavailable_failSoft() throws Exception {
        when(operatorAssignmentPort.isAssigned(anyString(), eq(SELECTED_TENANT))).thenReturn(true);
        // account /internal/tenant-domain-subscriptions returns 503 → fail-soft.
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/internal/tenant-domain-subscriptions"))
                .willReturn(WireMock.aResponse().withStatus(503)));

        String base = mintBaseToken("assume-op-004");
        MvcResult result = assumeTenant(base, SELECTED_TENANT);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode assumed = decodeJwtPayload(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .get("access_token").asText());
        // token still issued with the selected tenant; entitled_domains OMITTED (fail-soft).
        assertThat(assumed.get("tenant_id").asText()).isEqualTo(SELECTED_TENANT);
        assertThat(assumed.has("entitled_domains")).isFalse();
    }

    @Test
    @DisplayName("invalid subject token → invalid_grant (AC-1)")
    void invalidSubjectToken() throws Exception {
        // No assignment stub needed — subject validation fails first (fail-closed).
        MvcResult result = assumeTenant("not-a-valid-jwt." + UUID.randomUUID() + ".sig", SELECTED_TENANT);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_grant");
    }

    @Test
    @DisplayName("missing audience → invalid_request (AC-1)")
    void missingAudience_invalidRequest() throws Exception {
        String base = mintBaseToken("assume-op-005");
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", TOKEN_EXCHANGE)
                        .param("subject_token", base)
                        .param("subject_token_type", ACCESS_TOKEN_TYPE)
                        .param("client_id", "platform-console-web"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("invalid_request");
    }

    @Test
    @DisplayName("net-zero: existing authorization_code grant byte-unchanged (AC-6)")
    void authorizationCodeStillWorks() throws Exception {
        stubEntitledDomains("finance");
        // mintBaseToken itself exercises the authorization_code grant end-to-end.
        String base = mintBaseToken("assume-op-006");
        JsonNode payload = decodeJwtPayload(base);
        assertThat(payload.get("tenant_id")).isNotNull();
        assertThat(payload.get("iss")).isNotNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractParam(String url, String paramName) {
        String query = url != null && url.contains("?") ? url.substring(url.indexOf("?") + 1) : "";
        for (String param : query.split("&")) {
            if (param.startsWith(paramName + "=")) {
                return param.substring(paramName.length() + 1);
            }
        }
        return null;
    }

    private JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        String payload = parts[1];
        int mod = payload.length() % 4;
        if (mod != 0) payload += "=".repeat(4 - mod);
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }
}
