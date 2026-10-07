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

    /**
     * The {@code Authorization} header the adapter last sent to the masterdata stub, or
     * {@code null} if it sent none (TASK-ERP-BE-041).
     */
    protected static volatile String masterSeenAuthorization;

    // ------------------------------------------------------------------------
    // Person registry (TASK-MONO-776 — approval-api.md § v2.4).
    //
    // DEFAULT CONVENTION, so the pre-776 ITs keep their meaning: a token whose sub is
    // "emp-x" belongs to an account that is linked to the ACTIVE employee "emp-x", and an
    // approver id "emp-y" is an ACTIVE employee linked to the account "emp-y". That makes
    // the two id spaces coincide on purpose for those tests — they pin lifecycle behaviour,
    // not the id space. Tests about the id space (PersonIdSpaceIntegrationTest) register
    // DISTINCT account and employee ids below and so are not covered by the convention.
    // ------------------------------------------------------------------------

    /** account sub → linked employee id (overrides the identity convention). */
    protected static final java.util.Map<String, String> ACCOUNT_LINKS =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** accounts linked to no employee → `/me` answers 404. */
    protected static final java.util.Set<String> UNLINKED_ACCOUNTS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** employee id → {status, accountId-or-null} (overrides the identity convention). */
    protected static final java.util.Map<String, String[]> EMPLOYEES =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** employee ids that do not exist → `/approver-ref` answers 404. */
    protected static final java.util.Set<String> MISSING_EMPLOYEES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Non-200 forces `/employees/me` to fail with that status («could not ask who I am»). */
    protected static volatile int meHttpStatus = 200;
    /** Non-200 forces `/approver-ref` to fail with that status («could not ask about the approver»). */
    protected static volatile int approverRefHttpStatus = 200;

    /** Call from a test's {@code @BeforeEach}: back to the identity convention. */
    protected static void resetPeople() {
        ACCOUNT_LINKS.clear();
        UNLINKED_ACCOUNTS.clear();
        EMPLOYEES.clear();
        MISSING_EMPLOYEES.clear();
        meHttpStatus = 200;
        approverRefHttpStatus = 200;
    }

    /** Register an employee with a status and (nullable) linked account. */
    protected static void employee(String id, String status, String accountId) {
        EMPLOYEES.put(id, new String[]{status, accountId});
        if (accountId != null) {
            ACCOUNT_LINKS.put(accountId, id);
        }
    }

    private static MockResponse personResponse(String[] codeAndBody) {
        return new MockResponse().setResponseCode(Integer.parseInt(codeAndBody[0]))
                .setHeader("Content-Type", "application/json")
                .setBody(codeAndBody[1]);
    }

    private static String[] meResponse(String rawToken) {
        if (meHttpStatus != 200) {
            return new String[]{String.valueOf(meHttpStatus), "{\"code\":\"X\"}"};
        }
        String sub;
        try {
            sub = SignedJWT.parse(rawToken).getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            return new String[]{"401", "{\"code\":\"UNAUTHORIZED\"}"};
        }
        if (UNLINKED_ACCOUNTS.contains(sub)) {
            return new String[]{"404", "{\"code\":\"MASTERDATA_NOT_FOUND\"}"};
        }
        String employeeId = ACCOUNT_LINKS.getOrDefault(sub, sub);
        String[] e = EMPLOYEES.getOrDefault(employeeId, new String[]{"ACTIVE", sub});
        return new String[]{"200", personBody(employeeId, e[0], e[1])};
    }

    private static String[] approverRefResponse(String employeeId) {
        if (approverRefHttpStatus != 200) {
            return new String[]{String.valueOf(approverRefHttpStatus), "{\"code\":\"X\"}"};
        }
        if (MISSING_EMPLOYEES.contains(employeeId)) {
            return new String[]{"404", "{\"code\":\"MASTERDATA_NOT_FOUND\"}"};
        }
        String[] e = EMPLOYEES.getOrDefault(employeeId, new String[]{"ACTIVE", employeeId});
        return new String[]{"200", personBody(employeeId, e[0], e[1])};
    }

    /** {@code accountId} ABSENT when unlinked — masterdata's NON_NULL convention. */
    private static String personBody(String id, String status, String accountId) {
        return "{\"data\":{\"id\":\"" + id + "\",\"status\":\"" + status + "\""
                + (accountId == null ? "" : ",\"accountId\":\"" + accountId + "\"")
                + "},\"meta\":{}}";
    }

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
                // TASK-ERP-BE-041 — the real masterdata-service is an independent OIDC
                // resource server, so an unauthenticated call gets 401. This stub used to
                // answer 200 to ANY request, which is precisely why the production adapter
                // could ship with no Authorization header and every IT stay green. The
                // stub's predicate is now the resource server's predicate.
                String authorization = request.getHeader("Authorization");
                masterSeenAuthorization = authorization;
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    return new MockResponse().setResponseCode(401)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"code\":\"UNAUTHORIZED\"}");
                }
                String path = request.getPath() == null ? "" : request.getPath();
                // TASK-MONO-776 — person lookups (`/employees/me`, `/employees/{id}/approver-ref`)
                // are answered from the person registry below, NOT from masterStatus /
                // masterHttpStatus: those two drive the E1 subject check, and letting them
                // leak into the caller's own `/me` would turn every «subject 404» test into
                // «caller not linked».
                if (path.endsWith("/employees/me")) {
                    return personResponse(meResponse(authorization.substring("Bearer ".length())));
                }
                if (path.endsWith("/approver-ref")) {
                    String rest = path.substring(0, path.length() - "/approver-ref".length());
                    return personResponse(approverRefResponse(
                            rest.substring(rest.lastIndexOf('/') + 1)));
                }
                if (masterHttpStatus != 200) {
                    return new MockResponse().setResponseCode(masterHttpStatus)
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"code\":\"MASTERDATA_NOT_FOUND\"}");
                }
                String id = path.isEmpty() ? "x" : path.substring(path.lastIndexOf('/') + 1);
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
