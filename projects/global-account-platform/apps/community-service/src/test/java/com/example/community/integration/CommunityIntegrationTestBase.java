package com.example.community.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.gap.security.jwt.Rs256JwtSigner;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base class for community-service integration tests (TASK-BE-149).
 *
 * <p>Extends {@link AbstractIntegrationTest} to inherit the JVM-shared MySQL + Kafka
 * Testcontainers. Adds two service-local WireMock servers on dynamic ports for
 * membership-service and account-service, plus an RS256 JWT signer using the same key pair
 * as the runtime {@code keys/public.pem}.
 *
 * <p>Subclasses inherit the {@code @ActiveProfiles("test")} pin and the Docker
 * availability gate (via {@link AbstractIntegrationTest}); they should declare
 * {@code @SpringBootTest} (and optionally {@code @AutoConfigureMockMvc}) themselves.
 */
@ActiveProfiles("test")
public abstract class CommunityIntegrationTestBase extends AbstractIntegrationTest {

    protected static final WireMockServer MEMBERSHIP_WM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    protected static final WireMockServer ACCOUNT_WM =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    private static Rs256JwtSigner signer;

    @BeforeAll
    static void startWireMock() throws Exception {
        if (!MEMBERSHIP_WM.isRunning()) {
            MEMBERSHIP_WM.start();
        }
        if (!ACCOUNT_WM.isRunning()) {
            ACCOUNT_WM.start();
        }
        signer = new Rs256JwtSigner(loadTestPrivateKey(), "test-key-001");
    }

    @AfterAll
    static void stopWireMock() {
        if (MEMBERSHIP_WM.isRunning()) {
            MEMBERSHIP_WM.stop();
        }
        if (ACCOUNT_WM.isRunning()) {
            ACCOUNT_WM.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        MEMBERSHIP_WM.resetAll();
        ACCOUNT_WM.resetAll();
    }

    @DynamicPropertySource
    static void configureWireMock(DynamicPropertyRegistry registry) {
        registry.add("community.membership-service.base-url", MEMBERSHIP_WM::baseUrl);
        registry.add("community.account-service.base-url", ACCOUNT_WM::baseUrl);
    }

    protected String bearerToken(String accountId, List<String> roles) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", accountId);
        claims.put("roles", roles);
        claims.put("iss", "global-account-platform");
        claims.put("iat", Instant.now());
        claims.put("exp", Instant.now().plus(30, ChronoUnit.MINUTES));
        return "Bearer " + signer.sign(claims);
    }

    private static PrivateKey loadTestPrivateKey() throws Exception {
        try (InputStream is = CommunityIntegrationTestBase.class.getResourceAsStream("/keys/private.pem")) {
            if (is == null) {
                throw new IllegalStateException("classpath:/keys/private.pem not found");
            }
            String pem = new String(is.readAllBytes())
                    .replaceAll("-----BEGIN [A-Z ]+-----", "")
                    .replaceAll("-----END [A-Z ]+-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
    }
}
