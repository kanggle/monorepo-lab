package com.example.auth.infrastructure.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Spring Authorization Server 슬라이스 테스트 — Docker 없이 실행 가능.
 *
 * <p>Testcontainers 없이 H2 인메모리 DB + Kafka/Redis 비활성화로 SAS 레이어만 검증한다.
 *
 * <p>Phase 1 검증 범위 (TASK-BE-251):
 * <ul>
 *   <li>{@code GET /.well-known/openid-configuration} — discovery document 구조</li>
 *   <li>{@code GET /oauth2/jwks} — JWKS 구조 (RSA key, kid, n, e)</li>
 *   <li>{@code POST /oauth2/token} (client_credentials) — access token + tenant_id, tenant_type claims</li>
 *   <li>기존 {@code /api/auth/login} 경로 접근성 (SAS가 가로채지 않음)</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // --- Infrastructure stubs (no Testcontainers needed) ---
        // Use H2 in-memory instead of MySQL
        "spring.datasource.url=jdbc:h2:mem:sastest;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Disable Flyway (schema created by Hibernate ddl-auto)
        "spring.flyway.enabled=false",
        // Disable Kafka auto-configuration
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        // Redis — use a dummy host; RedisAutoConfiguration will be present but
        // the outbox polling scheduler is throttled to avoid connection errors
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6399",
        "spring.data.redis.timeout=1ms",
        // Disable outbox polling to avoid Redis/Kafka connection errors in tests
        "outbox.polling.interval-ms=99999999",
        // Account-service stub (not called in SAS tests)
        "auth.account-service.base-url=http://localhost:19999",
        // SAS issuer
        "oidc.issuer-url=http://localhost",
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2AuthorizationServerSliceTest {

    // Kafka is excluded from auto-config but AuthOutboxPollingScheduler still
    // declares KafkaTemplate as a constructor dependency — mock it out.
    @MockBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    private static final String CLIENT_ID = "test-internal-client";
    private static final String CLIENT_SECRET = "secret";
    private static final String BASIC_AUTH =
            "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // 1. OIDC Discovery
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("GET /.well-known/openid-configuration → 200 + required fields")
    void discovery_returns200() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode doc = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(doc.has("issuer")).isTrue();
        assertThat(doc.has("jwks_uri")).isTrue();
        assertThat(doc.has("token_endpoint")).isTrue();
        assertThat(doc.get("jwks_uri").asText()).contains("/oauth2/jwks");
        assertThat(doc.get("token_endpoint").asText()).contains("/oauth2/token");

        // grant_types_supported must include client_credentials
        JsonNode grantTypes = doc.get("grant_types_supported");
        assertThat(grantTypes).isNotNull();
        boolean clientCredentialsPresent = false;
        for (JsonNode gt : grantTypes) {
            if ("client_credentials".equals(gt.asText())) {
                clientCredentialsPresent = true;
                break;
            }
        }
        assertThat(clientCredentialsPresent)
                .as("grant_types_supported must contain client_credentials")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 2. JWKS
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("GET /oauth2/jwks → 200 + RSA key with kid, n, e")
    void jwks_returns200WithRsaKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andReturn();

        JsonNode firstKey = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("keys").get(0);

        assertThat(firstKey.get("kid").asText()).isNotBlank();
        assertThat(firstKey.get("n").asText()).isNotBlank();
        assertThat(firstKey.get("e").asText()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // 3. client_credentials token endpoint
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("POST /oauth2/token (client_credentials) → access token with tenant_id=fan-platform, tenant_type=B2C")
    void clientCredentials_returnsTokenWithTenantClaims() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("access_token").asText();

        // Decode JWT payload (Base64url, no signature verification needed for unit-level check)
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);
        String payloadB64 = parts[1];
        int mod = payloadB64.length() % 4;
        if (mod != 0) payloadB64 += "=".repeat(4 - mod);
        JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(payloadB64));

        // tenant_id must be present and correct (fail-closed check)
        assertThat(payload.has("tenant_id"))
                .as("access token must contain tenant_id claim")
                .isTrue();
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");

        // tenant_type must be present and correct
        assertThat(payload.has("tenant_type"))
                .as("access token must contain tenant_type claim")
                .isTrue();
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");

        // Standard JWT claims
        assertThat(payload.has("iss")).isTrue();
        assertThat(payload.has("exp")).isTrue();
    }

    @Test
    @Order(4)
    @DisplayName("POST /oauth2/token with wrong client secret → 401")
    void clientCredentials_wrongSecret_returns401() throws Exception {
        String badAuth = "Basic " + Base64.getEncoder()
                .encodeToString((CLIENT_ID + ":wrongsecret").getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, badAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    @DisplayName("POST /oauth2/token with unknown client → 401")
    void clientCredentials_unknownClient_returns401() throws Exception {
        String unknownAuth = "Basic " + Base64.getEncoder()
                .encodeToString("unknown-client:secret".getBytes());

        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, unknownAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // 4. Regression — SAS must NOT capture /api/auth/login
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("POST /api/auth/login → routed to legacy handler (SAS chain must not intercept)")
    void regression_loginEndpointNotCapturedBySAS() throws Exception {
        // A bad login request should reach the legacy handler and return 4xx,
        // NOT 404 (route not found) which would indicate SAS captured it.
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"regression@example.com","password":"any"}
                                """))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status)
                .as("Legacy /api/auth/login must not be captured by SAS chain. Got HTTP " + status)
                .isBetween(400, 499);
        assertThat(status)
                .as("404 means SAS swallowed /api/auth/login — not acceptable")
                .isNotEqualTo(404);
    }
}
