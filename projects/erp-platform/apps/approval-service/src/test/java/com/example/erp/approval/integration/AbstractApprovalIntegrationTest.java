package com.example.erp.approval.integration;

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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Base for approval-service integration tests. Shared MySQL (H2 forbidden) +
 * Kafka (outbox relay) containers + a JWKS MockWebServer + a SEPARATE WireMock
 * masterdata-service stub, all started once per JVM. The masterdata stub backs
 * the submit-time subject reference-integrity check (E1).
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractApprovalIntegrationTest {

    protected static final String TENANT_ERP = "erp";
    protected static final String ISSUER = "http://test-issuer";

    @SuppressWarnings("resource")
    protected static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("erp_db")
                    .withUsername("erp")
                    .withPassword("erp")
                    .withStartupTimeout(Duration.ofMinutes(3));

    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @SuppressWarnings("resource")
    protected static final MockWebServer JWKS = new MockWebServer();

    /** Stand-in masterdata-service for the E1 subject ref-check. */
    @SuppressWarnings("resource")
    protected static final MockWebServer MASTERDATA = new MockWebServer();

    private static volatile String jwksBody = "{\"keys\":[]}";
    /** Toggled per test: ACTIVE (default) / RETIRED / 404 for the subject stub. */
    protected static volatile String masterStatus = "ACTIVE";
    protected static volatile int masterHttpStatus = 200;

    private static RSAKey rsaKey;

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
        MASTERDATA.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (masterHttpStatus != 200) {
                    return new MockResponse().setResponseCode(masterHttpStatus)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"code\":\"MASTERDATA_NOT_FOUND\"}");
                }
                String id = request.getPath() == null ? "x"
                        : request.getPath().substring(request.getPath().lastIndexOf('/') + 1);
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"data\":{\"id\":\"" + id + "\",\"status\":\""
                                + masterStatus + "\"},\"meta\":{}}");
            }
        });
        try {
            JWKS.start();
            MASTERDATA.start();
            rsaKey = new RSAKeyGenerator(2048).keyID("test-key-approval").generate();
            jwksBody = "{\"keys\":[" + rsaKey.toPublicJWK().toJSONString() + "]}";
        } catch (Exception e) {
            throw new IllegalStateException("IT base setup failed", e);
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
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> JWKS.url("/oauth2/jwks").toString());
        registry.add("erpplatform.oauth2.allowed-issuers", () -> ISSUER);
        registry.add("erpplatform.approval.masterdata.base-url",
                () -> "http://localhost:" + MASTERDATA.getPort());
    }

    @Autowired(required = false)
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** Mint an RS256 token for {@code actorId} with the given scope. */
    protected String token(String actorId, String scope) throws Exception {
        return token(actorId, scope, TENANT_ERP, null);
    }

    protected String token(String actorId, String scope, String tenant,
                           List<String> entitledDomains) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(actorId)
                .issuer(ISSUER)
                .claim("tenant_id", tenant)
                .claim("scope", scope)
                .claim("org_scope", "*")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)));
        if (entitledDomains != null) {
            claims.claim("entitled_domains", entitledDomains);
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }
}
