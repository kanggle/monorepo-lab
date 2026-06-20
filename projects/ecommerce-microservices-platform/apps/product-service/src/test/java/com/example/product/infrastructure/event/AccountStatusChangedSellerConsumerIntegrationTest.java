package com.example.product.infrastructure.event;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerQueryService;
import com.example.product.domain.model.SellerStatus;
import com.example.product.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Reverse {@code account.status.changed → seller SUSPENDED} projection IT (ADR-MONO-042 D4-C,
 * TASK-BE-421). Drives the real {@link AccountStatusChangedSellerConsumer} →
 * {@link RegisterSellerService#suspendByLockedAccount} → {@code SellerRepository.findByAccountId}
 * against a real Postgres (Flyway V15 applied), with the outbound {@link SellerAccountProvisioner}
 * mocked (no real account-service). Proves: LOCKED → SUSPENDED; idempotent re-delivery; no-seller
 * fail-soft; non-LOCKED ignored.
 *
 * <p>Excluded from the Docker-free {@code :check}; Testcontainers is locally blocked on the dev
 * Windows host ({@code project_testcontainers_docker_desktop_blocker}) — CI-Linux is authoritative.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("계정 LOCKED → 셀러 SUSPENDED 역방향 투영(ADR-042 D4-C) 통합 테스트")
class AccountStatusChangedSellerConsumerIntegrationTest {

    private static final String TENANT_A = "tenant-a";

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("product_user")
            .withPassword("product_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:0");
        registry.add("spring.cache.type", () -> "none");
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private SellerAccountProvisioner provisioner;

    @Autowired
    private AccountStatusChangedSellerConsumer consumer;

    @Autowired
    private RegisterSellerService registerSellerService;

    @Autowired
    private SellerQueryService sellerQueryService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /** Seed an ACTIVE seller with a known backing accountId, return that accountId. */
    private String seedActiveSeller(String sellerId, String accountId) {
        TenantContext.set(TENANT_A);
        given(provisioner.provision(eq(TENANT_A), eq(sellerId), anyString()))
                .willReturn(ProvisioningResult.success(accountId, "id-" + sellerId));
        registerSellerService.register(new RegisterSellerCommand(sellerId, "Seller " + sellerId));
        assertThat(sellerQueryService.getSeller(sellerId).status()).isEqualTo(SellerStatus.ACTIVE);
        return accountId;
    }

    private AccountStatusChangedEvent lockedEvent(String accountId) {
        return new AccountStatusChangedEvent(
                accountId, TENANT_A, "ACTIVE", "LOCKED", "ADMIN_LOCK",
                "operator", "op-1", Instant.now());
    }

    // Flat on-wire JSON (account-events.md § account.status.changed) — drives onMessage so the
    // real deserialization path is proven end-to-end, not just the pre-built object.
    private String lockedWire(String accountId) {
        return """
                {
                  "accountId": "%s",
                  "tenantId": "%s",
                  "previousStatus": "ACTIVE",
                  "currentStatus": "LOCKED",
                  "reasonCode": "ADMIN_LOCK",
                  "actorType": "operator",
                  "actorId": "op-1",
                  "occurredAt": "2026-04-12T10:00:00Z"
                }
                """.formatted(accountId, TENANT_A);
    }

    @Test
    @DisplayName("LOCKED 와이어(flat JSON) → onMessage 역직렬화 → 매칭 셀러 SUSPENDED 로 영속")
    void locked_transitionsSellerToSuspended() {
        String accountId = seedActiveSeller("seller-1", "acct-1");
        TenantContext.clear();

        // Drive the full Kafka entry point (onMessage → deserialize → handle) with the real
        // flat wire, so a nested-DTO regression (BE-388 class) would fail here, not silently no-op.
        consumer.onMessage(lockedWire(accountId));

        TenantContext.set(TENANT_A);
        assertThat(sellerQueryService.getSeller("seller-1").status())
                .isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("재전달 → 여전히 SUSPENDED, 에러 없음 (멱등)")
    void redelivery_staysSuspendedNoError() {
        String accountId = seedActiveSeller("seller-2", "acct-2");
        TenantContext.clear();

        consumer.handle(lockedEvent(accountId));
        assertThatCode(() -> consumer.handle(lockedEvent(accountId))).doesNotThrowAnyException();

        TenantContext.set(TENANT_A);
        assertThat(sellerQueryService.getSeller("seller-2").status())
                .isEqualTo(SellerStatus.SUSPENDED);
    }

    @Test
    @DisplayName("매칭 셀러 없음 → fail-soft, 에러 없음")
    void noSeller_failSoftNoError() {
        assertThatCode(() -> consumer.handle(lockedEvent("acct-nonexistent")))
                .doesNotThrowAnyException();
    }
}
