package com.example.account.application.service;

import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.result.LeaveConsumerSiteResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteLeftBy;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-619 — «사이트 탈퇴» writes ONE site's membership and that site's roles, nothing else; every "no"
 * is a 409 {@code SITE_MEMBERSHIP_REQUIRED} with nothing written.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("LeaveConsumerSiteUseCase (TASK-BE-619)")
class LeaveConsumerSiteUseCaseTest {

    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000619000";
    private static final TenantId STORE = new TenantId("ecommerce");

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @InjectMocks private LeaveConsumerSiteUseCase useCase;

    private static Tenant tenant(String id, TenantType type) {
        return Tenant.reconstitute(new TenantId(id), id, type, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }

    private static Account poolAccount() {
        return Account.reconstitute(ACCOUNT, TenantId.CONSUMER_POOL, "pool@example.com", null,
                AccountStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH, null, null, null, 0);
    }

    private void storeAndPoolAccount() {
        given(tenantRepository.findById(STORE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.of(poolAccount()));
    }

    @Test
    @DisplayName("본인 탈퇴 → 그 사이트 멤버십만 LEFT(SELF) · 그 사이트 역할 삭제 · 계정은 그대로(ACTIVE)")
    void selfLeave_writesThisSiteOnly() {
        storeAndPoolAccount();
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                ConsumerSiteMembership.joinOnSignup(ACCOUNT, STORE, Instant.EPOCH)));
        given(membershipRepository.update(any())).willReturn(true);
        given(membershipRepository.removeAllSiteRoles(STORE, ACCOUNT)).willReturn(1);

        LeaveConsumerSiteResult r = useCase.execute("ecommerce", ACCOUNT, ConsumerSiteLeftBy.SELF, ACCOUNT);

        ArgumentCaptor<ConsumerSiteMembership> written = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
        verify(membershipRepository).update(written.capture());
        assertThat(written.getValue().getSiteTenantId()).isEqualTo(STORE);
        assertThat(written.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.LEFT);
        assertThat(written.getValue().getLeftBy()).isEqualTo(ConsumerSiteLeftBy.SELF);
        assertThat(r.changed()).isTrue();
        assertThat(r.leftBy()).isEqualTo("SELF");
        assertThat(r.accountStatus()).isEqualTo("ACTIVE");
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 LEFT 인 멤버십을 본인이 다시 → 쓰기 0 (멱등) · 지금 행을 답한다")
    void alreadyLeft_isNoOp() {
        storeAndPoolAccount();
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                ConsumerSiteMembership.joinOnSignup(ACCOUNT, STORE, Instant.EPOCH)
                        .leave(ConsumerSiteLeftBy.OPERATOR, "op-1", Instant.EPOCH)));

        LeaveConsumerSiteResult r = useCase.execute("ecommerce", ACCOUNT, ConsumerSiteLeftBy.SELF, ACCOUNT);

        assertThat(r.changed()).isFalse();
        assertThat(r.leftBy()).isEqualTo("OPERATOR");
        verify(membershipRepository, never()).update(any());
        verify(membershipRepository, never()).removeAllSiteRoles(any(), anyString());
    }

    @Test
    @DisplayName("사이트 계정(풀 아님) → 409 SITE_MEMBERSHIP_REQUIRED · 쓰기 0 — 사이트 계정의 «탈퇴» 는 계정 삭제다")
    void siteAccount_isRefused() {
        given(tenantRepository.findById(STORE)).willReturn(Optional.of(tenant("ecommerce", TenantType.B2C_CONSUMER)));
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("ecommerce", ACCOUNT, ConsumerSiteLeftBy.SELF, ACCOUNT))
                .isInstanceOf(SiteMembershipRequiredException.class);
        verifyNoInteractions(membershipRepository);
    }

    @Test
    @DisplayName("멤버십 행 없음 → 409 · 쓰기 0")
    void noMembership_isRefused() {
        storeAndPoolAccount();
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("ecommerce", ACCOUNT, ConsumerSiteLeftBy.OPERATOR, "op-1"))
                .isInstanceOf(SiteMembershipRequiredException.class);
        verify(membershipRepository, never()).update(any());
    }

    @Test
    @DisplayName("소비자 사이트가 아님(B2B) → 409 · 계정 조회도 없다")
    void nonConsumerSite_isRefused() {
        given(tenantRepository.findById(new TenantId("wms"))).willReturn(Optional.of(tenant("wms", TenantType.B2B_ENTERPRISE)));

        assertThatThrownBy(() -> useCase.execute("wms", ACCOUNT, ConsumerSiteLeftBy.OPERATOR, "op-1"))
                .isInstanceOf(SiteMembershipRequiredException.class);
        verifyNoInteractions(accountRepository, membershipRepository);
    }
}
