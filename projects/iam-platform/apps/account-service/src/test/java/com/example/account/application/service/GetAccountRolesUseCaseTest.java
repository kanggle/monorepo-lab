package com.example.account.application.service;

import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-618 (task 정정 ⑤) — the roles GET widening: a site tenant asked about a pool account that is an
 * ACTIVE member of that site answers {@code consumer_site_roles}; everything else is the old read.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("GetAccountRolesUseCase (TASK-BE-368 · TASK-BE-618 widening)")
class GetAccountRolesUseCaseTest {

    private static final TenantId FAN = new TenantId("fan-platform");
    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000618002";

    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private ConsumerPoolFlag flag;
    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @InjectMocks private GetAccountRolesUseCase useCase;

    private static Tenant tenant(String id, TenantType type) {
        return Tenant.reconstitute(new TenantId(id), id, type, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }

    private static ConsumerSiteMembership membership(ConsumerSiteMembershipStatus status) {
        return ConsumerSiteMembership.reconstitute(ACCOUNT, FAN, status, Instant.EPOCH);
    }

    @Test
    @DisplayName("저장 역할이 있으면 그것 — 넓힘 경로를 타지 않는다")
    void storedRoles_unchanged() {
        AccountRole role = mock(AccountRole.class);
        given(role.getRoleName()).willReturn("WAREHOUSE_ADMIN");
        given(accountRoleRepository.findByTenantIdAndAccountId(new TenantId("wms"), ACCOUNT)).willReturn(List.of(role));

        assertThat(useCase.execute("wms", ACCOUNT)).containsExactly("WAREHOUSE_ADMIN");
        verifyNoInteractions(flag, tenantRepository, accountRepository, membershipRepository);
    }

    @Test
    @DisplayName("플래그 꺼짐 → [] 그대로")
    void flagOff_empty() {
        given(accountRoleRepository.findByTenantIdAndAccountId(FAN, ACCOUNT)).willReturn(List.of());
        given(flag.isEnabled()).willReturn(false);

        assertThat(useCase.execute("fan-platform", ACCOUNT)).isEmpty();
        verifyNoInteractions(tenantRepository, accountRepository, membershipRepository);
    }

    @Test
    @DisplayName("소비자 사이트 + 풀 계정 + ACTIVE 멤버 → consumer_site_roles (이동한 팬의 ARTIST)")
    void poolMember_activeMembership_siteRoles() {
        given(accountRoleRepository.findByTenantIdAndAccountId(FAN, ACCOUNT)).willReturn(List.of());
        given(flag.isEnabled()).willReturn(true);
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(tenant("fan-platform", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(membership(ConsumerSiteMembershipStatus.ACTIVE)));
        given(membershipRepository.findSiteRoles(FAN, ACCOUNT)).willReturn(List.of("ARTIST", "FAN"));

        assertThat(useCase.execute("fan-platform", ACCOUNT)).containsExactly("ARTIST", "FAN");
    }

    @Test
    @DisplayName("LEFT 멤버십 → [] (넓히지 않는다)")
    void poolMember_leftMembership_empty() {
        given(accountRoleRepository.findByTenantIdAndAccountId(FAN, ACCOUNT)).willReturn(List.of());
        given(flag.isEnabled()).willReturn(true);
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(tenant("fan-platform", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
        given(membershipRepository.find(FAN, ACCOUNT)).willReturn(Optional.of(membership(ConsumerSiteMembershipStatus.LEFT)));

        assertThat(useCase.execute("fan-platform", ACCOUNT)).isEmpty();
    }

    @Test
    @DisplayName("B2B 테넌트 → [] (풀 조회 없음)")
    void b2bTenant_empty() {
        TenantId wms = new TenantId("wms");
        given(accountRoleRepository.findByTenantIdAndAccountId(wms, ACCOUNT)).willReturn(List.of());
        given(flag.isEnabled()).willReturn(true);
        given(tenantRepository.findById(wms)).willReturn(Optional.of(tenant("wms", TenantType.B2B_ENTERPRISE)));

        assertThat(useCase.execute("wms", ACCOUNT)).isEmpty();
        verifyNoInteractions(accountRepository, membershipRepository);
    }

    @Test
    @DisplayName("풀 계정이 아니면(사이트 계정 · 없는 계정) → []")
    void notPoolAccount_empty() {
        given(accountRoleRepository.findByTenantIdAndAccountId(FAN, ACCOUNT)).willReturn(List.of());
        given(flag.isEnabled()).willReturn(true);
        given(tenantRepository.findById(FAN)).willReturn(Optional.of(tenant("fan-platform", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());

        assertThat(useCase.execute("fan-platform", ACCOUNT)).isEmpty();
        verifyNoInteractions(membershipRepository);
    }
}
