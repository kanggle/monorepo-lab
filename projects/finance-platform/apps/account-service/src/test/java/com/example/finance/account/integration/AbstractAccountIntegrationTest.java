package com.example.finance.account.integration;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.command.UpgradeKycCommand;
import com.example.finance.account.application.view.AccountView;
import com.example.testsupport.integration.DockerAvailableCondition;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Base class for account-service integration tests.
 *
 * <p>Shared <b>MySQL</b> (production-parity engine — H2 is FORBIDDEN) + Kafka
 * (libs/java-messaging outbox relay) + Redis (idempotency primary) containers,
 * started once per JVM. JWKS is stubbed per-class with an OkHttp
 * {@code MockWebServer} in the subclasses that exercise the HTTP/JWT layer.
 *
 * <p>Most ITs drive the application layer directly (OAuth2 JWT validation is
 * bypassed at that level); the cross-tenant IT goes through the HTTP layer
 * with a signed token to assert the 403 fail-closed path.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractAccountIntegrationTest {

    protected static final String TENANT_FINANCE = "finance";

    /** Standard fund-holder actor (no operator role) used across the ITs. */
    protected static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT_FINANCE, Set.of());
    /** Operator actor (OPERATOR role) — e.g. KYC upgrades. */
    protected static final ActorContext OPERATOR =
            new ActorContext("op-1", TENANT_FINANCE, Set.of("OPERATOR"));

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("finance_db")
                    .withUsername("finance")
                    .withPassword("finance")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    /**
     * Base-owned JWKS endpoint. There must be exactly ONE
     * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
     * registration: a second {@code @DynamicPropertySource} in a subclass that
     * adds the same key is non-deterministically shadowed by this base method
     * (the prior cross-tenant IT failed for exactly that reason). So the base
     * owns the only registration and points it at a reachable MockWebServer;
     * application-layer ITs never present a token (empty key set is fine), and
     * a subclass that drives the HTTP+JWT path publishes its signing key's
     * public JWK via {@link #publishJwks(String)} in {@code @BeforeAll}.
     */
    private static volatile String jwksBody = "{\"keys\":[]}";

    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS = new MockWebServer();

    /**
     * Single RSA signing key shared by the HTTP+JWT subclasses. Its public JWK is
     * served from {@link #JWKS} once a subclass calls {@link #publishSigningKey()}
     * in {@code @BeforeAll}; tokens are minted via {@link #token(Consumer)}.
     */
    private static final RSAKey RSA_KEY = generateRsaKey();

    private static RSAKey generateRsaKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("account-test-key").generate();
        } catch (Exception e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    /** Replace the served JWK set (subclass HTTP+JWT path, in {@code @BeforeAll}). */
    protected static void publishJwks(String jwksJson) {
        jwksBody = jwksJson;
    }

    /**
     * Publish {@link #RSA_KEY}'s public JWK into the base-owned JWKS server. The
     * HTTP+JWT subclasses call this in {@code @BeforeAll} (before the resource
     * server lazily fetches the JWK set on the first request). The single
     * {@code jwk-set-uri} registration lives in this base — subclasses must NOT
     * add a second {@code @DynamicPropertySource} for it.
     */
    protected static void publishSigningKey() {
        publishJwks("{\"keys\":[" + RSA_KEY.toPublicJWK().toJSONString() + "]}");
    }

    /**
     * Mint a signed RS256 token. Common claims (subject {@code user-1}, issuer
     * {@code http://test-issuer}, issued-now, +300s expiry) are pre-populated; the
     * {@code customizer} adds the per-test claims (tenant_id / scope / roles /
     * entitled_domains — each subclass keeps its own claim shape).
     */
    protected String token(Consumer<JWTClaimsSet.Builder> customizer) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer("http://test-issuer")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(RSA_KEY.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    /**
     * Open an account and upgrade it to FULL KYC / ACTIVE via the application
     * command boundary. Shared "open → active" fixture for the ITs that need a
     * usable account; only the owner ref varies.
     */
    protected AccountView openActiveFullKyc(AccountApplicationService service, String ownerRef) {
        AccountView opened = service.openAccount(new OpenAccountCommand(
                HOLDER, ownerRef, "KRW", "NONE"));
        return service.upgradeKyc(new UpgradeKycCommand(
                OPERATOR, opened.accountId(), "FULL", "kyc verified"));
    }

    static {
        MYSQL.start();
        KAFKA.start();
        JWKS.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody(jwksBody);
            }
        });
        try {
            JWKS.start();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("JWKS MockWebServer start failed", e);
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // Single jwk-set-uri registration (base-owned reachable MockWebServer).
        // App-layer ITs never present a token; the HTTP+JWT IT publishes its
        // public JWK via publishJwks(...) in @BeforeAll before its first request.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("financeplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
        // Deterministic sanction list for the F4 IT.
        registry.add("financeplatform.account.compliance.sanctioned-owner-refs",
                () -> "SANCTIONED-OWNER");
    }

    @Autowired(required = false)
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
}
