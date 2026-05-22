package com.example.scmplatform.procurement.integration;

import com.example.common.id.UuidV7;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.AuditLogJpaRepository;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PurchaseOrderJpaRepository;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.PurchaseOrderLineJpaRepository;
import com.example.scmplatform.procurement.infrastructure.persistence.jpa.SupplierJpaRepository;
import com.example.testsupport.integration.DockerAvailableCondition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Base class for procurement-service integration tests.
 *
 * <p>Provides shared PostgreSQL + Kafka + Redis containers (shared per JVM via
 * static fields) and common test-data factory methods.  Each subclass may add
 * its own containers (e.g. a per-class MockWebServer for the supplier stub).
 *
 * <p>DB engine: PostgreSQL (production dialect) instead of the MySQL used by
 * {@link com.example.testsupport.integration.AbstractIntegrationTest} because
 * procurement-service declares {@code PostgreSQLDialect} in its production
 * application.yml.
 *
 * <p>Security: all IT routes call the application layer directly (via
 * {@link com.example.scmplatform.procurement.application.PurchaseOrderApplicationService})
 * rather than through the HTTP layer, so OAuth2 JWT validation is bypassed at
 * the application level.  HTTP-layer tests that DO need JWT tokens use a
 * dedicated OkHttp MockWebServer to serve a JWKS stub.
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractProcurementIntegrationTest {

    protected static final String TENANT_SCM = "scm";
    protected static final String TENANT_OTHER = "tenant-other";

    // -----------------------------------------------------------------------
    // Shared containers — started once per JVM, outlive all Spring contexts.
    // -----------------------------------------------------------------------

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("scm_procurement")
                    .withUsername("scm")
                    .withPassword("scm")
                    .withStartupTimeout(Duration.ofMinutes(3));

    // KRaft mode (cp-kafka 7.4+) emits `[RaftManager id=N] Completed transition to Leader(...)`
    // instead of the legacy `[KafkaServer id=N] started` line, so the old log-message
    // waiting strategy times out. ConfluentKafkaContainer ships its own internal wait
    // strategy that handles KRaft-mode startup correctly — defer to it.
    protected static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        KAFKA.start();
        // REDIS is managed by @Container / @Testcontainers on each subclass.
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        // Disable JWKS auto-discovery — tests that need JWT use their own stubs.
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://localhost:9999/oauth2/jwks");
        registry.add("scmplatform.oauth2.allowed-issuers", () -> "http://test-issuer");
        // Supplier mock URL is overridden per-class in subclasses.
    }

    // -----------------------------------------------------------------------
    // Shared JPA repositories for test-data setup and assertion.
    // -----------------------------------------------------------------------

    @Autowired
    protected PurchaseOrderJpaRepository poJpa;

    @Autowired
    protected PurchaseOrderLineJpaRepository lineJpa;

    @Autowired
    protected SupplierJpaRepository supplierJpa;

    @Autowired
    protected AuditLogJpaRepository auditLogJpa;

    // -----------------------------------------------------------------------
    // Factory helpers
    // -----------------------------------------------------------------------

    /**
     * Persists an ACTIVE supplier in the given tenant. Returns the saved entity.
     */
    protected Supplier persistActiveSupplier(String tenantId) {
        String id = UuidV7.randomString();
        Supplier s = Supplier.create(id, tenantId, "Test Supplier " + id, SupplierStatus.ACTIVE);
        return supplierJpa.save(s);
    }

    /**
     * Persists a DRAFT PO with a single line (qty=10, price=5.00 USD) in the
     * given tenant, linked to the supplied supplierId.
     */
    protected PurchaseOrder persistDraftPo(String tenantId, String supplierId) {
        String poId = UuidV7.randomString();
        // Use last 12 chars of the UUID (pure random bits) to avoid timestamp
        // collision when multiple tests call this helper within the same millisecond.
        String suffix = poId.replace("-", "").substring(20).toUpperCase();
        String poNumber = "PO-IT-" + suffix;
        PurchaseOrder po = PurchaseOrder.createDraft(
                poId, tenantId, poNumber, supplierId, "buyer-it-001", "USD");
        PurchaseOrderLine line = PurchaseOrderLine.create(
                UuidV7.randomString(), poId, tenantId,
                1, "sku-it-001", "sup-sku-it-001",
                new BigDecimal("10"), new BigDecimal("5.00"));
        po.addLine(line);

        // Persist PO then lines separately (mirrors PurchaseOrderRepositoryAdapter.save()).
        poJpa.save(po);
        lineJpa.save(line);
        return po;
    }
}
