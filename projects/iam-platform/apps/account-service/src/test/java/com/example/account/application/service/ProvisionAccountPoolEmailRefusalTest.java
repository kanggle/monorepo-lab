package com.example.account.application.service;

import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-616 (multi-tenancy.md § 소비자 계정 풀 § 2, reverse direction) — internal account creation into a
 * consumer site is refused when the email already has a POOL account (the existing duplicate answer,
 * 409 ACCOUNT_ALREADY_EXISTS). B2B / customer tenants keep per-tenant accounts (D1).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TASK-BE-616 — 내부 프로비저닝 · 풀 계정 이메일 거절 (§ 2 역방향)")
class ProvisionAccountPoolEmailRefusalTest {

    private static final String EMAIL = "shopper@example.com";

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private AuthServicePort authServicePort;
    @Mock private AccountIdentityProvisioner accountIdentityProvisioner;

    private static Tenant tenant(String id, TenantType type) {
        return Tenant.reconstitute(new TenantId(id), id, type, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }

    private ConsumerAccountPool pool() {
        return new ConsumerAccountPool(() -> true, tenantRepository, accountRepository, membershipRepository);
    }

    private ProvisionAccountUseCase useCase() {
        return new ProvisionAccountUseCase(tenantRepository, accountRepository, profileRepository,
                accountRoleRepository, historyRepository, eventPublisher, authServicePort,
                accountIdentityProvisioner, pool());
    }

    @Test
    @DisplayName("ecommerce 에 프로비저닝 · 그 이메일의 풀 계정 있음 → 409 ACCOUNT_ALREADY_EXISTS · 계정 저장·자격·이벤트 0")
    void consumerSite_emailHasPoolAccount_refused() {
        given(tenantRepository.findById(new TenantId("ecommerce")))
                .willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        given(accountRepository.existsByEmail(new TenantId("ecommerce"), EMAIL)).willReturn(false);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(true);

        assertThatThrownBy(() -> useCase().execute(new ProvisionAccountCommand(
                "ecommerce", EMAIL, "Password1!", "Seller", null, null, List.of("SELLER"), "op-1")))
                .isInstanceOf(AccountAlreadyExistsException.class);

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(authServicePort, eventPublisher, profileRepository, accountRoleRepository);
    }

    @Test
    @DisplayName("ecommerce · 풀 계정 없음 → 거절하지 않는다(풀 조회 1회)")
    void consumerSite_noPoolAccount_passes() {
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(false);

        assertThatCode(() -> pool().refuseIfEmailHasPoolAccount(
                tenant("ecommerce", TenantType.B2C_CONSUMER), EMAIL, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대조군 (D1): B2B(wms) · 고객 테넌트(demo-corp) → 풀 이메일이어도 거절 없음 · 풀 조회조차 없음")
    void b2bAndCustomerTenants_neverRefused_noPoolLookup() {
        assertThatCode(() -> pool().refuseIfEmailHasPoolAccount(
                tenant("wms", TenantType.B2B_ENTERPRISE), EMAIL, EMAIL)).doesNotThrowAnyException();
        assertThatCode(() -> pool().refuseIfEmailHasPoolAccount(
                tenant("demo-corp", TenantType.B2B_ENTERPRISE), EMAIL, EMAIL)).doesNotThrowAnyException();
        assertThatCode(() -> pool().refuseIfEmailHasPoolAccount(
                tenant("consumer-pool", TenantType.B2C_CONSUMER), EMAIL, EMAIL)).doesNotThrowAnyException();

        verifyNoInteractions(accountRepository);
    }
}
