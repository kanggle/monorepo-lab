package com.example.product.presentation.controller;

import com.example.product.ProductServiceApplication;
import com.example.product.application.command.RegisterSellerCommand;
import com.example.product.application.port.SellerAccountProvisioner;
import com.example.product.application.port.SellerAccountProvisioner.ProvisioningResult;
import com.example.product.application.port.SellerSiteRoleGateway;
import com.example.product.application.service.RegisterSellerService;
import com.example.product.application.service.SellerMemberService;
import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-752 against a real Postgres (Flyway V22): {@code seller_members} / {@code seller_member_invitations}
 * through the real service + repositories — the JPQL the in-memory unit tests stand in for (the conditional
 * {@code markAccepted}, the «ACTIVE elsewhere in an ACTIVE seller» join, re-activation of a REVOKED row), and that
 * only the token hash is stored. IAM is the mocked {@link SellerSiteRoleGateway}.
 *
 * <p>Docker-only ({@code integrationTest}); CI-Linux is authoritative.
 */
@SpringBootTest(classes = ProductServiceApplication.class)
@Tag("integration")
@Testcontainers
@DisplayName("셀러 구성원 · 초대 영속 통합 테스트 (TASK-MONO-752)")
class SellerMemberIntegrationTest {

    private static final String TENANT = "tenant-752";
    private static final String PERSON = "0199de70-0000-7000-8000-00000000a752";

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

    @MockitoBean @SuppressWarnings("unused") private KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoBean private SellerAccountProvisioner provisioner;
    @MockitoBean private SellerSiteRoleGateway siteRoleGateway;

    @Autowired private RegisterSellerService registerSellerService;
    @Autowired private SellerMemberService sellerMemberService;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void activeSeller(String sellerId) {
        given(provisioner.provision(eq(TENANT), eq(sellerId), anyString()))
                .willReturn(ProvisioningResult.success("machine-" + sellerId, null));
        registerSellerService.register(new RegisterSellerCommand(sellerId, "Seller " + sellerId));
    }

    @Test
    @DisplayName("초대(해시만 저장) → 수락 → ACTIVE 행 · 재사용 거절 · 두 셀러 중 하나 정지는 역할 유지 · 둘 다 정지면 회수")
    void inviteAcceptSuspend_roundTrip() {
        TenantContext.set(TENANT);
        activeSeller("s-a");
        activeSeller("s-b");

        SellerMemberService.IssuedInvitation a = sellerMemberService.invite("s-a", "Member@Example.com", "op-1");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM seller_member_invitations WHERE id = ?",
                String.class, a.invitation().getId()))
                .hasSize(64).isNotEqualTo(a.token());
        assertThat(jdbc.queryForObject("SELECT email FROM seller_member_invitations WHERE id = ?",
                String.class, a.invitation().getId())).isEqualTo("member@example.com");

        sellerMemberService.accept(a.token(), PERSON);
        verify(siteRoleGateway).grant(TENANT, PERSON, "member@example.com");
        assertThat(jdbc.queryForObject("SELECT status FROM seller_members WHERE tenant_id = ? AND seller_id = 's-a' "
                + "AND account_id = ?", String.class, TENANT, PERSON)).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> sellerMemberService.accept(a.token(), "0199de70-0000-7000-8000-00000000ffff"))
                .isInstanceOf(SellerInvitationAlreadyUsedException.class);

        sellerMemberService.accept(sellerMemberService.invite("s-b", "member@example.com", "op-1").token(), PERSON);

        given(siteRoleGateway.revoke(TENANT, PERSON)).willReturn(true);
        registerSellerService.suspend("s-a");
        verify(siteRoleGateway, never()).revoke(TENANT, PERSON); // s-b still ACTIVE with this person ACTIVE
        assertThat(sellerMemberService.list("s-a").members().get(0).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);

        registerSellerService.close("s-b");
        verify(siteRoleGateway).revoke(TENANT, PERSON);
        assertThat(sellerMemberService.list("s-b").members().get(0).getStatus()).isEqualTo(SellerMemberStatus.REVOKED);
    }
}
