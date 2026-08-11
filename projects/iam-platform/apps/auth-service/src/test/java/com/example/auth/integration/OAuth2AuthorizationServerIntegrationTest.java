package com.example.auth.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Spring Authorization Server (SAS) OIDC endpoints.
 *
 * <p>Phase 1 scope (TASK-BE-251):
 * <ol>
 *   <li>{@code GET /.well-known/openid-configuration} — discovery document</li>
 *   <li>{@code GET /oauth2/jwks} — JWKS endpoint</li>
 *   <li>{@code POST /oauth2/token} with {@code grant_type=client_credentials} —
 *       access token + tenant_id claim</li>
 * </ol>
 *
 * <p>The Phase 1 test client ({@code test-internal-client / secret}) is registered as an
 * in-memory placeholder in
 * {@link com.example.auth.infrastructure.oauth2.AuthorizationServerConfig}.
 *
 * <p><b>The class has outgrown that sentence, and correcting it is not cosmetic.</b> Cases 7-8
 * (TASK-BE-317 / TASK-BE-515) authenticate {@code account-service-client} and cases 9-12
 * (TASK-BE-579) authenticate {@code community-service-client} — both <b>Flyway-seeded</b>
 * rows (V0019 / V0009+V0032), exercised through the real token endpoint. TASK-BE-579 was
 * filed on the premise that this class "never touches a seeded row", which is what the
 * paragraph above still implied; the ticket read the javadoc rather than the cases and
 * concluded the issuer side had no precedent anywhere. It had one, here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OAuth2AuthorizationServerIntegrationTest extends AbstractIntegrationTest {

    // Redis — service-specific container (not shared via AbstractIntegrationTest)
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        // WireMock is not needed for SAS endpoint tests; set a safe default
        registry.add("auth.account-service.base-url", () -> "http://localhost:19999");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** TASK-BE-579: reads the seeded rows directly, so a token assertion and a row assertion can disagree. */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Basic auth header for test-internal-client / secret
    private static final String CLIENT_ID = "test-internal-client";
    private static final String CLIENT_SECRET = "secret";
    private static final String BASIC_AUTH_HEADER =
            "Basic " + Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

    // -----------------------------------------------------------------------
    // 1. OIDC Discovery
    // -----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("GET /.well-known/openid-configuration returns 200 with required fields")
    void oidcDiscovery_returns200WithRequiredFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.issuer").isNotEmpty())
                .andExpect(jsonPath("$.jwks_uri").isNotEmpty())
                .andExpect(jsonPath("$.token_endpoint").isNotEmpty())
                .andExpect(jsonPath("$.response_types_supported").isArray())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode doc = objectMapper.readTree(body);

        assertThat(doc.get("issuer").asText()).isNotBlank();
        assertThat(doc.get("jwks_uri").asText()).contains("/oauth2/jwks");
        assertThat(doc.get("token_endpoint").asText()).contains("/oauth2/token");

        // grant_types_supported must include client_credentials
        JsonNode grantTypes = doc.get("grant_types_supported");
        assertThat(grantTypes).isNotNull();
        boolean hasClientCredentials = false;
        for (JsonNode grantType : grantTypes) {
            if ("client_credentials".equals(grantType.asText())) {
                hasClientCredentials = true;
                break;
            }
        }
        assertThat(hasClientCredentials)
                .as("grant_types_supported must contain client_credentials")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // 2. JWKS
    // -----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("GET /oauth2/jwks returns 200 with RSA key")
    void jwks_returns200WithRsaKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode jwks = objectMapper.readTree(body);
        JsonNode firstKey = jwks.get("keys").get(0);

        assertThat(firstKey.get("kty").asText()).isEqualTo("RSA");
        assertThat(firstKey.get("use").asText()).isEqualTo("sig");
        assertThat(firstKey.get("kid").asText()).isNotBlank();
        assertThat(firstKey.get("n").asText()).isNotBlank();
        assertThat(firstKey.get("e").asText()).isNotBlank();

        // alg field — SAS may or may not include it; RS256 is the default
        if (firstKey.has("alg")) {
            assertThat(firstKey.get("alg").asText()).isEqualTo("RS256");
        }
    }

    // -----------------------------------------------------------------------
    // 3. client_credentials token endpoint
    // -----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("POST /oauth2/token (client_credentials) returns access token with tenant_id claim")
    void clientCredentials_returnsAccessTokenWithTenantIdClaim() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, BASIC_AUTH_HEADER)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "account.read"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode tokenResponse = objectMapper.readTree(body);
        String accessToken = tokenResponse.get("access_token").asText();

        // Decode JWT payload (without verification — SAS signed it with our own key)
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);

        byte[] payloadBytes = Base64.getUrlDecoder().decode(
                parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
        JsonNode payload = objectMapper.readTree(payloadBytes);

        // tenant_id claim must be present (fail-closed guard in TenantClaimTokenCustomizer)
        assertThat(payload.has("tenant_id"))
                .as("access token must contain tenant_id claim")
                .isTrue();
        assertThat(payload.get("tenant_id").asText()).isEqualTo("fan-platform");

        // tenant_type claim must be present
        assertThat(payload.has("tenant_type"))
                .as("access token must contain tenant_type claim")
                .isTrue();
        assertThat(payload.get("tenant_type").asText()).isEqualTo("B2C");

        // Standard claims
        assertThat(payload.get("iss").asText()).isNotBlank();
        assertThat(payload.get("exp").asLong()).isGreaterThan(0L);
    }

    @Test
    @Order(4)
    @DisplayName("POST /oauth2/token with wrong client secret returns 401")
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
    @DisplayName("POST /oauth2/token with unknown client returns 401")
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
    // 4. TASK-BE-398 — the legacy /api/auth/login surface is sunset
    // -----------------------------------------------------------------------

    @Test
    @Order(6)
    @DisplayName("TASK-BE-398: POST /api/auth/login no longer issues a custom JWT (ADR-001 D2-b sunset)")
    void legacyLoginEndpointIsSunset() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"regression@example.com","password":"any"}
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .as("the retired /api/auth/login must never return 2xx. Got " + status)
                            .isGreaterThanOrEqualTo(400);
                    assertThat(result.getResponse().getContentAsString())
                            .as("no access token may be issued by the retired endpoint")
                            .doesNotContain("accessToken");
                });
    }

    // -----------------------------------------------------------------------
    // 5. TASK-BE-317 — GAP-internal service workload client (ADR-005 단계 1)
    // -----------------------------------------------------------------------

    @Test
    @Order(7)
    @DisplayName("TASK-BE-317: account-service-client (client_credentials) issues a JWKS-verifiable JWT with INTERNAL tenant claims")
    void clientCredentials_internalServiceClient_issuesJwksVerifiableToken() throws Exception {
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("account-service-client:secret".getBytes());

        // AC-1: token endpoint returns 200 + access_token for the seeded GAP-internal svc client.
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "internal.invoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("access_token").asText();

        // AC-2: the token is verifiable via the GAP JWKS (signature) and carries the GAP issuer
        // plus the service-identity / INTERNAL tenant claims.
        String jwksJson = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAKey rsaKey = JWKSet.parse(jwksJson).getKeys().get(0).toRSAKey();
        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        Jwt jwt = decoder.decode(accessToken); // throws if the signature or exp is invalid
        assertThat(jwt.getIssuer()).as("iss claim must be the GAP issuer").isNotNull();
        assertThat(jwt.getSubject())
                .as("client_credentials principal == client_id (service identity)")
                .isEqualTo("account-service-client");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("global-account-platform");
        assertThat(jwt.getClaimAsString("tenant_type")).isEqualTo("INTERNAL");
    }

    @Test
    @Order(8)
    @DisplayName("TASK-BE-515: client_credentials grants a scope ONLY when explicitly requested — "
            + "no scope param → no internal.invoke claim (the /internal/** RED mechanism); "
            + "scope=internal.invoke → claim present")
    void clientCredentials_scopeClaim_grantedOnlyWhenRequested() throws Exception {
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString("account-service-client:secret".getBytes());

        // Shared JWKS decoder for both minted tokens.
        String jwksJson = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAPublicKey publicKey = JWKSet.parse(jwksJson).getKeys().get(0).toRSAKey().toRSAPublicKey();
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

        // (a) WITH scope=internal.invoke — the receiver-required scope IS present in the token.
        String withScopeToken = objectMapper.readTree(mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "internal.invoke"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("access_token").asText();
        assertThat(scopesOf(decoder.decode(withScopeToken)))
                .as("explicit scope=internal.invoke must appear in the minted token's scope claim")
                .contains("internal.invoke");

        // (b) WITHOUT any scope param — SAS grants NO scopes, so internal.invoke is absent. This is the
        // exact mechanism behind the TASK-BE-514/MONO-422 nightly RED: the four IAM workload providers
        // used to send no scope, so their tokens failed the /internal/** RequiredScopeValidator (401).
        // TASK-BE-515 makes them request the scope; this negative case guards the regression.
        String noScopeToken = objectMapper.readTree(mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, basicAuth)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("access_token").asText();
        assertThat(scopesOf(decoder.decode(noScopeToken)))
                .as("no scope param → SAS grants no scopes → internal.invoke absent (the RED mechanism)")
                .doesNotContain("internal.invoke");
    }

    // -----------------------------------------------------------------------
    // 6. TASK-BE-579 — the V0032 `artist.read` grant, verified on the ISSUER side
    // -----------------------------------------------------------------------
    //
    // What was missing. TASK-FAN-BE-045 made community-service validate follow
    // targets against artist-service synchronously and FAIL-CLOSED, and granted
    // `community-service-client` the `artist.read` machine scope (V0032) so its
    // workload token would be accepted. Both tests that covered the seam sit on
    // the CONSUMER side and mint their own credentials: fan's
    // InternalArtistAuthIntegrationTest signs its JWTs with JwtTestHelper, and
    // FollowArtistGateIntegrationTest stubs iam's token endpoint with a
    // MockWebServer that returns the literal string "stub-workload-token". Both
    // therefore ASSUME the grant they depend on.
    //
    // Why the assumption is worth a test. The ways this grant can fail are all
    // silent: JSON_ARRAY_APPEND writing the wrong shape, the `oauth_scopes`
    // catalog row missing, or the is_system/tenant_id combination not matching
    // the lookup. Every one of them surfaces at RUNTIME as `invalid_scope` →
    // checker fails closed → EVERY follow refused — and a fail-closed outage is
    // indistinguishable from a working security control (V0032's own header says
    // so). Nothing would be red; follows would simply stop.
    //
    // Sibling proximity over a new class, deliberately: cases 7-8 above are the
    // same shape (seeded client, real token endpoint, JWKS-verified, positive +
    // negative scope) and already carry `scopesOf`. A separate @SpringBootTest
    // class would mean a second Spring context and a second Redis container on a
    // lane that is sharded for wall-clock (TASK-MONO-438).
    //
    // 🔴 The ticket said this class only exercises the in-memory placeholder
    // `test-internal-client` and "never touches a seeded row". That is what the
    // CLASS JAVADOC says — it still describes Phase 1 (TASK-BE-251) — but cases
    // 7-8 (BE-317/BE-515) use `account-service-client`, which V0019 seeds. The
    // precedent was inside the class the ticket dismissed.

    /** V0009 seeds this client for tenant `fan-platform`; V0032 appends `artist.read`. */
    private static final String COMMUNITY_CLIENT_ID = "community-service-client";

    /**
     * V0009 and V0019 carry the SAME bcrypt hash, and case 7 authenticates
     * `account-service-client:secret` against it — so the seeded secret is
     * `secret` for this client too. (Measured, not assumed: if it were not, the
     * request below would 401 rather than silently pass.)
     */
    private static final String COMMUNITY_CLIENT_SECRET = "secret";

    private static String basicAuthFor(String clientId, String clientSecret) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());
    }

    private String mintToken(String clientId, String clientSecret, String scope) throws Exception {
        var request = post("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, basicAuthFor(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials");
        if (scope != null) {
            request = request.param("scope", scope);
        }
        return objectMapper.readTree(mockMvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("access_token").asText();
    }

    /** The GAP JWKS decoder — a token that does not verify against it is not one we issued. */
    private JwtDecoder jwksDecoder() throws Exception {
        String jwksJson = mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        RSAPublicKey publicKey = JWKSet.parse(jwksJson).getKeys().get(0).toRSAKey().toRSAPublicKey();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Test
    @Order(9)
    @DisplayName("TASK-BE-579 AC-1: SAS actually issues `artist.read` to the SEEDED community-service-client")
    void clientCredentials_communityClient_isIssuedArtistReadScope() throws Exception {
        Jwt jwt = jwksDecoder().decode(
                mintToken(COMMUNITY_CLIENT_ID, COMMUNITY_CLIENT_SECRET, "artist.read"));

        assertThat(scopesOf(jwt))
                .as("artist-service's Order(1) internal chain grants ROLE_INTERNAL only on this "
                        + "scope; without it every follow is refused fail-closed (V0032 header)")
                .contains("artist.read");
        assertThat(jwt.getSubject())
                .as("client_credentials principal == client_id (service identity)")
                .isEqualTo(COMMUNITY_CLIENT_ID);
        // Pins the row we believe we are exercising: auth-api.md § OAuth2 Clients lists this
        // client under tenant `fan-platform`. A token minted from some other client_id that
        // happened to hold the scope would satisfy the assertion above but not this one.
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("fan-platform");
    }

    @Test
    @Order(10)
    @DisplayName("TASK-BE-579 AC-2 (negative control): a scope this client does NOT hold → invalid_scope")
    void clientCredentials_communityClient_unheldScopeIsRejected() throws Exception {
        // `internal.invoke` is real and held by account-service-client (V0019) — NOT by this
        // one. A nonsense string would also be rejected, but by the catalog rather than by the
        // per-client grant, which is the thing under test.
        mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION,
                                basicAuthFor(COMMUNITY_CLIENT_ID, COMMUNITY_CLIENT_SECRET))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "internal.invoke"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
        // Without this case, AC-1 cannot tell "this client was granted artist.read" from
        // "this server hands out whatever scope is asked for".
    }

    @Test
    @Order(11)
    @DisplayName("TASK-BE-579: V0032 APPENDED — account.read / membership.read survived the JSON_ARRAY_APPEND")
    void clientCredentials_communityClient_retainsItsPreExistingScopes() throws Exception {
        Jwt jwt = jwksDecoder().decode(mintToken(
                COMMUNITY_CLIENT_ID, COMMUNITY_CLIENT_SECRET,
                "account.read membership.read artist.read"));

        assertThat(scopesOf(jwt))
                .as("if V0032 had overwritten the array instead of appending, the membership "
                        + "gate would fail closed and the premium feed would be blocked "
                        + "wholesale — a louder failure than the one V0032 was fixing, and one "
                        + "that asserting artist.read alone would not see")
                .contains("account.read", "membership.read", "artist.read");
    }

    @Test
    @Order(12)
    @DisplayName("TASK-BE-579 AC-1: the V0032 seed rows themselves — catalog entry + client grant, and the guard is idempotent")
    void v0032SeedRowsArePresentAndTheAppendIsIdempotent() {
        // The catalog row. Edge case named by the ticket: it is seeded with tenant_id NULL +
        // is_system TRUE to join the machine-scope family (account.read / membership.read).
        // A lookup that filtered by tenant would not find it — assert the combination, not
        // just the name.
        Integer catalogRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth_scopes "
                        + "WHERE scope_name = 'artist.read' AND tenant_id IS NULL AND is_system = TRUE",
                Integer.class);
        assertThat(catalogRows).as("V0032 catalog row for artist.read").isEqualTo(1);

        String scopesJson = jdbcTemplate.queryForObject(
                "SELECT scopes FROM oauth_clients WHERE client_id = ?", String.class,
                COMMUNITY_CLIENT_ID);
        assertThat(scopesJson).contains("artist.read", "account.read", "membership.read");

        // V0032 guards its UPDATE with JSON_SEARCH so a re-run is a no-op. Flyway will never
        // re-run it, so nothing else can catch a broken guard — and the demo seed path applies
        // migrations to long-lived databases where a duplicate would accumulate silently.
        jdbcTemplate.update(
                "UPDATE oauth_clients SET scopes = JSON_ARRAY_APPEND(scopes, '$', 'artist.read') "
                        + "WHERE client_id = ? AND JSON_SEARCH(scopes, 'one', 'artist.read') IS NULL",
                COMMUNITY_CLIENT_ID);

        String afterReRun = jdbcTemplate.queryForObject(
                "SELECT scopes FROM oauth_clients WHERE client_id = ?", String.class,
                COMMUNITY_CLIENT_ID);
        assertThat(afterReRun)
                .as("re-applying V0032's guarded UPDATE must change nothing")
                .isEqualTo(scopesJson);
    }

    /** Extract scopes from a JWT {@code scope} claim (space-delimited String or Collection; absent → empty). */
    private static java.util.List<String> scopesOf(Jwt jwt) {
        Object scope = jwt.getClaim("scope");
        if (scope == null) {
            return java.util.List.of();
        }
        if (scope instanceof java.util.Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return java.util.Arrays.stream(scope.toString().trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
