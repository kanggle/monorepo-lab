package com.example.account.application.service;

import com.example.account.application.result.ConsumerSiteMembershipResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.AccountRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-615 — the membership read auth-service signs a pool principal into a site with
 * (multi-tenancy.md § 소비자 계정 풀 § 4). Every "no" is an answer in the body, never a 404.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("GetConsumerSiteMembershipUseCase (TASK-BE-615)")
class GetConsumerSiteMembershipUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000615000";
    private static final TenantId ECOMMERCE = new TenantId("ecommerce");

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @InjectMocks private GetConsumerSiteMembershipUseCase useCase;

    private static Tenant tenant(String id, TenantType type) {
        return Tenant.reconstitute(new TenantId(id), id, type, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }

    private void poolAccountExists() {
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(mock(Account.class)));
    }

    @Test
    @DisplayName("ACTIVE 멤버 → status ACTIVE · 그 사이트 역할만 · 사이트 tenant_type")
    void activeMember_returnsSiteRoles() {
        given(tenantRepository.findById(ECOMMERCE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        poolAccountExists();
        given(membershipRepository.find(ECOMMERCE, ACCOUNT)).willReturn(Optional.of(
                ConsumerSiteMembership.reconstitute(ACCOUNT, ECOMMERCE, ConsumerSiteMembershipStatus.ACTIVE, Instant.EPOCH)));
        given(membershipRepository.findSiteRoles(ECOMMERCE, ACCOUNT)).willReturn(List.of("SELLER"));

        ConsumerSiteMembershipResult r = useCase.execute("ecommerce", ACCOUNT);

        assertThat(r.consumerSite()).isTrue();
        assertThat(r.siteTenantType()).isEqualTo("B2C_CONSUMER");
        assertThat(r.membershipStatus()).isEqualTo("ACTIVE");
        assertThat(r.siteRoles()).containsExactly("SELLER");
    }

    @Test
    @DisplayName("멤버십 없음 → status null · 역할 없음 (404 아님)")
    void noMembership_answersNull() {
        given(tenantRepository.findById(ECOMMERCE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        poolAccountExists();
        given(membershipRepository.find(ECOMMERCE, ACCOUNT)).willReturn(Optional.empty());

        ConsumerSiteMembershipResult r = useCase.execute("ecommerce", ACCOUNT);

        assertThat(r.consumerSite()).isTrue();
        assertThat(r.membershipStatus()).isNull();
        assertThat(r.siteRoles()).isEmpty();
        verify(membershipRepository, never()).findSiteRoles(any(), anyString());
    }

    @Test
    @DisplayName("LEFT 멤버십 → status LEFT · 역할은 싣지 않는다")
    void leftMembership_noRoles() {
        given(tenantRepository.findById(ECOMMERCE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        poolAccountExists();
        given(membershipRepository.find(ECOMMERCE, ACCOUNT)).willReturn(Optional.of(
                ConsumerSiteMembership.reconstitute(ACCOUNT, ECOMMERCE, ConsumerSiteMembershipStatus.LEFT, Instant.EPOCH)));

        ConsumerSiteMembershipResult r = useCase.execute("ecommerce", ACCOUNT);

        assertThat(r.membershipStatus()).isEqualTo("LEFT");
        assertThat(r.siteRoles()).isEmpty();
        verify(membershipRepository, never()).findSiteRoles(any(), anyString());
    }

    @Test
    @DisplayName("소비자 사이트가 아님(B2B wms) → consumerSite=false · 계정·멤버십 조회조차 없다")
    void nonConsumerSite_answersNotConsumerSite() {
        given(tenantRepository.findById(new TenantId("wms"))).willReturn(Optional.of(tenant("wms", TenantType.B2B_ENTERPRISE)));

        ConsumerSiteMembershipResult r = useCase.execute("wms", ACCOUNT);

        assertThat(r.consumerSite()).isFalse();
        assertThat(r.siteTenantType()).isEqualTo("B2B_ENTERPRISE");
        assertThat(r.membershipStatus()).isNull();
        verifyNoInteractions(accountRepository, membershipRepository);
    }

    @Test
    @DisplayName("풀 테넌트 자신 · 없는 테넌트 → consumerSite=false")
    void poolItselfOrUnknown_notConsumerSite() {
        given(tenantRepository.findById(TenantId.CONSUMER_POOL))
                .willReturn(Optional.of(tenant("consumer-pool", TenantType.B2C_CONSUMER)));
        given(tenantRepository.findById(new TenantId("nowhere"))).willReturn(Optional.empty());

        assertThat(useCase.execute("consumer-pool", ACCOUNT).consumerSite()).isFalse();
        ConsumerSiteMembershipResult unknown = useCase.execute("nowhere", ACCOUNT);
        assertThat(unknown.consumerSite()).isFalse();
        assertThat(unknown.siteTenantType()).isNull();
    }

    @Test
    @DisplayName("풀 계정이 아님(사이트 계정 id) → 멤버십 없음과 같은 답")
    void notAPoolAccount_noMembership() {
        given(tenantRepository.findById(ECOMMERCE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());

        ConsumerSiteMembershipResult r = useCase.execute("ecommerce", ACCOUNT);

        assertThat(r.consumerSite()).isTrue();
        assertThat(r.membershipStatus()).isNull();
        verifyNoInteractions(membershipRepository);
    }
}
