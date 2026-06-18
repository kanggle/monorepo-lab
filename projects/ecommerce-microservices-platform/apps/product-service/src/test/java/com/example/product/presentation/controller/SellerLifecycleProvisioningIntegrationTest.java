package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Seller onboarding → IAM provisioning → lifecycle persistence IT (ADR-MONO-042
 * M1/M2/M3, AC-1..AC-5). Drives the real {@code RegisterSellerService} +
 * {@code SellerRepository} against a real Postgres (Flyway V15 applied), with the
 * outbound {@code SellerAccountProvisioner} mocked (no real account-service) so the
 * fail-soft + idempotent + deactivation transitions are exercised end-to-end through
 * the persistence layer.
 *
 * <p>Proves: onboarding-success → ACTIVE + ids stored; provisioning-failure → seller
 * persisted PENDING_PROVISIONING (fail-soft); re-provision fills + flips to ACTIVE;
 * suspend → SUSPENDED + lock call; default seller stays ACTIVE never provisioned (D8).
 *
 * <p>Excluded from the Docker-free {@code :check} (no {@code -PrunIntegration}); the gate
 * is unit+slice. Testcontainers is locally blocked on the dev Windows host
 * ({@code project_testcontainers_docker_desktop_blocker}) — CI-Linux is authoritative.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("셀러 온보딩 IAM 프로비저닝 라이프사이클(ADR-042) 통합 테스트")
class SellerLifecycleProvisioningIntegrationTest {

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

    /** The outbound IAM provisioner is mocked — no real account-service in the IT. */
    @MockitoBean
    private SellerAccountProvisioner provisioner;

    @Autowired
    private RegisterSellerService registerSellerService;

    @Autowired
    private SellerQueryService sellerQueryService;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("온보딩 성공 provisioning → 셀러 ACTIVE + account/identity 저장 (AC-2)")
    void onboard_successfulProvisioning_persistsActive() {
        TenantContext.set(TENANT_A);
        given(provisioner.provision(eq(TENANT_A), eq("seller-1"), anyString()))
                .willReturn(ProvisioningResult.success("acct-1", "id-1"));

        registerSellerService.register(new com.example.product.application.command.RegisterSellerCommand(
                "seller-1", "Seller One"));

        assertThat(sellerQueryService.getSeller("seller-1").status())
                .isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("provisioning 실패 → 셀러 PENDING_PROVISIONING 영속 (AC-3 fail-soft)")
    void onboard_failedProvisioning_persistsPending() {
        TenantContext.set(TENANT_A);
        given(provisioner.provision(eq(TENANT_A), eq("seller-2"), anyString()))
                .willReturn(ProvisioningResult.failed());

        registerSellerService.register(new com.example.product.application.command.RegisterSellerCommand(
                "seller-2", "Seller Two"));

        assertThat(sellerQueryService.getSeller("seller-2").status())
                .isEqualTo(SellerStatus.PENDING_PROVISIONING);
    }

    @Test
    @DisplayName("PENDING 셀러 재-provision → ACTIVE 로 전이 (D3 retry)")
    void reprovision_pending_becomesActive() {
        TenantContext.set(TENANT_A);
        given(provisioner.provision(eq(TENANT_A), eq("seller-3"), anyString()))
                .willReturn(ProvisioningResult.failed());
        registerSellerService.register(new com.example.product.application.command.RegisterSellerCommand(
                "seller-3", "Seller Three"));
        assertThat(sellerQueryService.getSeller("seller-3").status())
                .isEqualTo(SellerStatus.PENDING_PROVISIONING);

        given(provisioner.provision(eq(TENANT_A), eq("seller-3"), anyString()))
                .willReturn(ProvisioningResult.success("acct-3", "id-3"));
        registerSellerService.provisionPending("seller-3");

        assertThat(sellerQueryService.getSeller("seller-3").status())
                .isEqualTo(SellerStatus.ACTIVE);
    }

    @Test
    @DisplayName("suspend → SUSPENDED 영속 + 백킹 계정 lock 호출 (AC-5)")
    void suspend_persistsSuspendedAndLocks() {
        TenantContext.set(TENANT_A);
        given(provisioner.provision(eq(TENANT_A), eq("seller-4"), anyString()))
                .willReturn(ProvisioningResult.success("acct-4", "id-4"));
        registerSellerService.register(new com.example.product.application.command.RegisterSellerCommand(
                "seller-4", "Seller Four"));

        registerSellerService.suspend("seller-4");

        assertThat(sellerQueryService.getSeller("seller-4").status())
                .isEqualTo(SellerStatus.SUSPENDED);
        verify(provisioner).lockAccount(eq(TENANT_A), eq("acct-4"));
    }

    @Test
    @DisplayName("default seller 는 ACTIVE 로 ensure 되고 provisioning 하지 않는다 (D8 anchor, AC-5)")
    void defaultSeller_isActive_neverProvisioned() {
        TenantContext.set(TENANT_A);

        registerSellerService.ensureDefaultSeller();

        assertThat(sellerQueryService.getSeller("default").status())
                .isEqualTo(SellerStatus.ACTIVE);
    }
}
