package com.example.account.application.service;

import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.SiteMembershipRequiredException;
import com.example.account.application.exception.SiteRoleEmailMismatchException;
import com.example.account.application.exception.SiteRoleNotGrantableException;
import com.example.account.application.exception.SiteRoleRequiresPoolAccountException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.SiteRoleMutationResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-MONO-752 (ADR-MONO-079 D5; consumer-site-roles.md) — the site-role writer.
 *
 * <p>🔴 The control group is the email rule (ticket Failure Scenario 1): the same pool account, the same
 * site, the same role — only {@code expectedEmail} differs, and only the matching one writes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumerSiteRoleWriteUseCase — 사이트 역할 쓰기/회수 (TASK-MONO-752)")
class ConsumerSiteRoleWriteUseCaseTest {

    private static final TenantId STORE = new TenantId("ecommerce");
    private static final TenantId FAN = new TenantId("fan-platform");
    private static final String ACCOUNT = "0199de70-0000-7000-8000-000000000752";
    private static final String EMAIL = "member@example.com";

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @InjectMocks private ConsumerSiteRoleWriteUseCase useCase;

    private static Tenant tenant(TenantId id, TenantType type) {
        return Tenant.reconstitute(id, id.value(), type, TenantStatus.ACTIVE, Instant.EPOCH, Instant.EPOCH);
    }

    private static Account account(TenantId tenant, String email) {
        return Account.reconstitute(ACCOUNT, tenant, email, null, AccountStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH, null, null, null, 0);
    }

    private void storeIsConsumerSite() {
        given(tenantRepository.findById(STORE)).willReturn(Optional.of(tenant(STORE, TenantType.B2C_CONSUMER)));
    }

    private void poolAccount() {
        given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT))
                .willReturn(Optional.of(account(TenantId.CONSUMER_POOL, EMAIL)));
    }

    private void activeStoreMembership() {
        given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                ConsumerSiteMembership.reconstitute(ACCOUNT, STORE, ConsumerSiteMembershipStatus.ACTIVE, Instant.EPOCH)));
    }

    @Nested
    @DisplayName("grant")
    class Grant {

        @Test
        @DisplayName("🔴 대조군: 같은 계정·사이트·역할에서 이메일만 다르면 거절, 맞으면 쓴다")
        void controlGroup_onlyMatchingEmailWrites() {
            storeIsConsumerSite();
            poolAccount();

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", "someone-else@example.com", "product-service"))
                    .isInstanceOf(SiteRoleEmailMismatchException.class);
            verify(membershipRepository, never()).addSiteRole(any(), anyString(), anyString(), any(), any());
            verifyNoInteractions(historyRepository);

            activeStoreMembership();
            given(membershipRepository.findSiteRoles(STORE, ACCOUNT)).willReturn(List.of(), List.of("SELLER"));

            SiteRoleMutationResult result = useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, "product-service");

            assertThat(result.changed()).isTrue();
            assertThat(result.roles()).containsExactly("SELLER");
            verify(membershipRepository).addSiteRole(org.mockito.ArgumentMatchers.eq(STORE),
                    org.mockito.ArgumentMatchers.eq(ACCOUNT), org.mockito.ArgumentMatchers.eq("SELLER"),
                    org.mockito.ArgumentMatchers.eq("product-service"), any(Instant.class));
        }

        @Test
        @DisplayName("이메일 비교는 대소문자·앞뒤 공백을 무시한다 (초대 주소와 계정 주소는 같은 사람)")
        void emailComparison_caseAndWhitespaceInsensitive() {
            storeIsConsumerSite();
            poolAccount();
            activeStoreMembership();
            given(membershipRepository.findSiteRoles(STORE, ACCOUNT)).willReturn(List.of(), List.of("SELLER"));

            assertThat(useCase.grant("ecommerce", ACCOUNT, "SELLER", "  Member@Example.COM ", null).changed()).isTrue();
        }

        @Test
        @DisplayName("쓰기 = (계정, ecommerce, SELLER) 한 행 + 사이트 테넌트 감사 1행 — 팬 사이트에는 아무것도 안 쓴다")
        void writesOnlyTheStoreRow_andAuditsUnderTheSite() {
            storeIsConsumerSite();
            poolAccount();
            activeStoreMembership();
            given(membershipRepository.findSiteRoles(STORE, ACCOUNT)).willReturn(List.of(), List.of("SELLER"));

            useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, "product-service");

            verify(membershipRepository, never()).addSiteRole(org.mockito.ArgumentMatchers.eq(FAN), anyString(),
                    anyString(), any(), any());
            ArgumentCaptor<AccountStatusHistoryEntry> audit = ArgumentCaptor.forClass(AccountStatusHistoryEntry.class);
            verify(historyRepository).save(audit.capture());
            assertThat(audit.getValue().getTenantId()).isEqualTo("ecommerce");
            assertThat(audit.getValue().getDetails()).contains("SITE_ROLE_GRANT").contains("SELLER");
            // the account itself is never re-saved: no status change on grant
            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 가진 역할 → changed=false · 쓰기·감사 없음 (멱등)")
        void alreadyHeld_noOp() {
            storeIsConsumerSite();
            poolAccount();
            activeStoreMembership();
            given(membershipRepository.findSiteRoles(STORE, ACCOUNT)).willReturn(List.of("SELLER"));

            SiteRoleMutationResult result = useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null);

            assertThat(result.changed()).isFalse();
            verify(membershipRepository, never()).addSiteRole(any(), anyString(), anyString(), any(), any());
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("멤버십 없음 → SITE_MEMBERSHIP_REQUIRED · 멤버십을 만들지 않는다")
        void noMembership_refused_neverCreatesOne() {
            storeIsConsumerSite();
            poolAccount();
            given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(SiteMembershipRequiredException.class);
            verify(membershipRepository, never()).insert(any());
            verify(membershipRepository, never()).addSiteRole(any(), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("LEFT 멤버십 → SITE_MEMBERSHIP_REQUIRED")
        void leftMembership_refused() {
            storeIsConsumerSite();
            poolAccount();
            given(membershipRepository.find(STORE, ACCOUNT)).willReturn(Optional.of(
                    ConsumerSiteMembership.reconstitute(ACCOUNT, STORE, ConsumerSiteMembershipStatus.LEFT, Instant.EPOCH)));

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(SiteMembershipRequiredException.class);
        }

        @Test
        @DisplayName("사이트 계정(풀 밖) → SITE_ROLE_REQUIRES_POOL_ACCOUNT (Edge Case 2)")
        void siteAccount_refused() {
            storeIsConsumerSite();
            given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());
            given(accountRepository.findById(STORE, ACCOUNT)).willReturn(Optional.of(account(STORE, EMAIL)));

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(SiteRoleRequiresPoolAccountException.class);
            verify(membershipRepository, never()).addSiteRole(any(), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("모르는 계정 → ACCOUNT_NOT_FOUND")
        void unknownAccount_notFound() {
            storeIsConsumerSite();
            given(accountRepository.findById(TenantId.CONSUMER_POOL, ACCOUNT)).willReturn(Optional.empty());
            given(accountRepository.findById(STORE, ACCOUNT)).willReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        @DisplayName("🔴 닫힌 목록: ECOMMERCE_OPERATOR 는 쓸 수 없다 — 계정 조회조차 하지 않는다")
        void operatorRole_notGrantable() {
            storeIsConsumerSite();

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "ECOMMERCE_OPERATOR", EMAIL, null))
                    .isInstanceOf(SiteRoleNotGrantableException.class);
            verifyNoInteractions(accountRepository);
            verify(membershipRepository, never()).addSiteRole(any(), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("팬 사이트의 SELLER 는 목록에 없다 — 평탄화 금지")
        void sellerOnFanSite_notGrantable() {
            given(tenantRepository.findById(FAN)).willReturn(Optional.of(tenant(FAN, TenantType.B2C_CONSUMER)));

            assertThatThrownBy(() -> useCase.grant("fan-platform", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(SiteRoleNotGrantableException.class);
        }

        @Test
        @DisplayName("소비자 사이트가 아닌 테넌트 → SITE_ROLE_NOT_GRANTABLE")
        void nonConsumerTenant_notGrantable() {
            given(tenantRepository.findById(STORE)).willReturn(Optional.of(tenant(STORE, TenantType.B2B_ENTERPRISE)));

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(SiteRoleNotGrantableException.class);
        }

        @Test
        @DisplayName("등록되지 않은 테넌트 → TENANT_NOT_FOUND")
        void unknownTenant() {
            given(tenantRepository.findById(STORE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.grant("ecommerce", ACCOUNT, "SELLER", EMAIL, null))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("🔴 SELLER 만 지운다 — 계정 잠금 없음 · 멤버십 그대로 (CUSTOMER·팬 로그인 유지)")
        void removesRole_neverLocksOrLeaves() {
            storeIsConsumerSite();
            poolAccount();
            given(membershipRepository.removeSiteRole(STORE, ACCOUNT, "SELLER")).willReturn(true);
            given(membershipRepository.findSiteRoles(STORE, ACCOUNT)).willReturn(List.of());

            SiteRoleMutationResult result = useCase.revoke("ecommerce", ACCOUNT, "SELLER", "product-service");

            assertThat(result.changed()).isTrue();
            assertThat(result.roles()).isEmpty();
            verify(accountRepository, never()).save(any());
            verify(membershipRepository, never()).insert(any());
            verify(historyRepository).save(any(AccountStatusHistoryEntry.class));
        }

        @Test
        @DisplayName("없는 역할 → changed=false · 감사 없음 (멱등)")
        void notHeld_noOp() {
            storeIsConsumerSite();
            poolAccount();
            given(membershipRepository.removeSiteRole(STORE, ACCOUNT, "SELLER")).willReturn(false);
            lenient().when(membershipRepository.findSiteRoles(STORE, ACCOUNT)).thenReturn(List.of());

            assertThat(useCase.revoke("ecommerce", ACCOUNT, "SELLER", null).changed()).isFalse();
            verifyNoInteractions(historyRepository);
        }

        @Test
        @DisplayName("회수도 닫힌 목록 밖은 거절")
        void revokeOutsideList_refused() {
            storeIsConsumerSite();

            assertThatThrownBy(() -> useCase.revoke("ecommerce", ACCOUNT, "CUSTOMER", null))
                    .isInstanceOf(SiteRoleNotGrantableException.class);
            verify(membershipRepository, never()).removeSiteRole(any(), anyString(), anyString());
        }
    }

    @Test
    @DisplayName("sameEmail — null·공백은 언제나 불일치")
    void sameEmail_nullsNeverMatch() {
        assertThat(ConsumerSiteRoleWriteUseCase.sameEmail(null, EMAIL)).isFalse();
        assertThat(ConsumerSiteRoleWriteUseCase.sameEmail(" ", EMAIL)).isFalse();
        assertThat(ConsumerSiteRoleWriteUseCase.sameEmail(EMAIL, null)).isFalse();
    }
}
