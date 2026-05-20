package com.kanggle.platformconsole.bff.integration;

import com.example.testsupport.integration.DockerAvailableCondition;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Base for console-bff integration tests.
 *
 * <p>console-bff is stateless (no DB / Kafka / Redis). Testcontainers is
 * included as baseline harness per AC-12 — this base class starts only a
 * MockWebServer for the GAP JWKS stub. The Spring context is fully booted
 * ({@code @SpringBootTest(webEnvironment = RANDOM_PORT)}).
 *
 * <p>AC-12 requirements:
 * <ol>
 *   <li>GAP JWKS stubbed via MockWebServer (WireMock alternative, same pattern
 *       as erp/finance precedent).</li>
 *   <li>{@code GET /actuator/health} returns 200.</li>
 *   <li>Per-domain {@code CredentialSelectionPort} 5-row dispatch dry-run.</li>
 *   <li>{@code GET /actuator/prometheus} exposes the 3 mandatory metric names.</li>
 *   <li>{@code OperatorCredentialContext} reads {@code X-Operator-Token} and
 *       {@code X-Tenant-Id} headers correctly.</li>
 * </ol>
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractConsoleBffIntegrationTest {

    /** MockWebServer that stubs the GAP JWKS endpoint. */
    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS_SERVER = new MockWebServer();

    private static volatile String jwksBody = "{\"keys\":[]}";

    protected static void publishJwks(String jwksJson) {
        jwksBody = jwksJson;
    }

    static {
        JWKS_SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(jwksBody);
            }
        });
        try {
            JWKS_SERVER.start();
        } catch (IOException e) {
            throw new IllegalStateException("JWKS MockWebServer start failed", e);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // Wire JWKS stub URL into Spring Security OAuth2 Resource Server config.
        String jwksUri = JWKS_SERVER.url("/oauth2/jwks").toString();
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> jwksUri);
        // Use a test issuer that we control (matches published JWKS tokens).
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://test-issuer");
    }

    @Autowired
    protected TestRestTemplate restTemplate;
}
