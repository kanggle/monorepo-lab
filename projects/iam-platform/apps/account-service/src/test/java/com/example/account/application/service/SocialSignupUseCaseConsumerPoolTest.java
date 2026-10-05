package com.example.account.application.service;

import com.example.account.application.command.SocialSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.SocialSignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-617 (ADR-MONO-078 D2 · D4; multi-tenancy.md § 소비자 계정 풀 § 2 · § 6;
 * auth-to-account-social.md) — the social signup on the consumer pool, with the real
 * {@link ConsumerAccountPool} rules over mocked ports. The DB-level proof is
 * {@code ConsumerPoolSocialSignupIntegrationTest}.
 *
 * <p>🔴 The AC-2 control is {@link FlagOn#passwordPoolAccountEmail_refused_neverLinked}: before this
 * ticket the social signup linked by email inside the tenant it was asked about; that step must never
 * reach the pool.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SocialSignupUseCase — 소비자 계정 풀 (TASK-BE-617)")
class SocialSignupUseCaseConsumerPoolTest {

    private static final TenantId FAN = TenantId.FAN_PLATFORM;
    private static final TenantId ECOMMERCE = new TenantId("ecommerce");
    private static final TenantId WMS = new TenantId("wms");
    private static final String EMAIL = "fan@example.com";

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private AccountIdentityProvisioner accountIdentityProvisioner;
    @Mock private TenantRepository tenantRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private ConsumerPoolFlag flag;

    private SocialSignupUseCase useCase;

    private static final Map<TenantId, TenantType> TENANTS = Map.of(
            FAN, TenantType.B2C_CONSUMER,
            ECOMMERCE, TenantType.B2C_CONSUMER,
            TenantId.CONSUMER_POOL, TenantType.B2C_CONSUMER,
            WMS, TenantType.B2B_ENTERPRISE);

    private static Tenant tenant(TenantId id) {
        return Tenant.reconstitute(id, id.value(), TENANTS.get(id), TenantStatus.ACTIVE,
                Instant.now(), Instant.now());
    }

    private static SocialSignupCommand socialFrom(String tenantId) {
        return new SocialSignupCommand(EMAIL, "GOOGLE", "google-617", "Fan", tenantId);
    }

    private static Account account(String id, TenantId tenantId) {
        return Account.reconstitute(id, tenantId, EMAIL, null, AccountStatus.ACTIVE,
                Instant.now(), Instant.now(), null, null, null, 0);
    }

    @BeforeEach
    void setUp() {
        useCase = new SocialSignupUseCase(accountRepository, profileRepository, eventPublisher,
                accountIdentityProvisioner, new ActiveTenantGuard(tenantRepository),
                new ConsumerAccountPool(flag, tenantRepository, accountRepository, membershipRepository));
        lenient().when(tenantRepository.findById(any(TenantId.class)))
                .thenAnswer(inv -> Optional.ofNullable(TENANTS.containsKey(inv.<TenantId>getArgument(0))
                        ? tenant(inv.getArgument(0)) : null));
        lenient().when(tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER))
                .thenReturn(List.of(tenant(TenantId.CONSUMER_POOL), tenant(ECOMMERCE), tenant(FAN)));
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(accountIdentityProvisioner.mintIdentity(anyString(), eq(EMAIL))).thenReturn("idy-617");
    }

    @Nested
    @DisplayName("플래그 켜짐")
    class FlagOn {

        @BeforeEach
        void on() {
            lenient().when(flag.isEnabled()).thenReturn(true);
        }

        @Test
        @DisplayName("AC-1: 팬 소셜 가입 → 풀 계정 + 팬 멤버십 + account.created(fan-platform) · 응답 tenantId=consumer-pool")
        void fanSocialSignup_bornInPool() {
            SocialSignupResult result = useCase.execute(socialFrom("fan-platform"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            Account account = saved.getValue();
            assertThat(account.getTenantId()).isEqualTo(TenantId.CONSUMER_POOL);
            assertThat(result.created()).isTrue();
            assertThat(result.tenantId()).isEqualTo("consumer-pool");

            ArgumentCaptor<ConsumerSiteMembership> membership = ArgumentCaptor.forClass(ConsumerSiteMembership.class);
            verify(membershipRepository).insert(membership.capture());
            assertThat(membership.getValue().getAccountId()).isEqualTo(account.getId());
            assertThat(membership.getValue().getSiteTenantId()).isEqualTo(FAN);
            assertThat(membership.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);

            verify(accountIdentityProvisioner).mintIdentity("consumer-pool", EMAIL);
            verify(accountRepository).assignIdentityId(TenantId.CONSUMER_POOL, account.getId(), "idy-617");
            verify(profileRepository).save(any(Profile.class));

            ArgumentCaptor<String> eventTenant = ArgumentCaptor.forClass(String.class);
            verify(eventPublisher).publishAccountCreated(eq(account), eventTenant.capture(), any());
            assertThat(eventTenant.getValue()).isEqualTo("fan-platform").isNotEqualTo("consumer-pool");
        }

        @Test
        @DisplayName("🔴 AC-2 대조군: 같은 이메일의 비밀번호 풀 계정이 있으면 → 409, 그 계정에 붙지 않는다 (계정·멤버십·이벤트 0)")
        void passwordPoolAccountEmail_refused_neverLinked() {
            given(accountRepository.findByEmail(FAN, EMAIL)).willReturn(Optional.empty());
            given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(true);
            // The pool account is reachable by email — the legacy step would have returned it. It must
            // never be read as the answer: the use case is not allowed to look it up at all.
            lenient().when(accountRepository.findByEmail(TenantId.CONSUMER_POOL, EMAIL))
                    .thenReturn(Optional.of(account("acc-password-pool", TenantId.CONSUMER_POOL)));

            assertThatThrownBy(() -> useCase.execute(socialFrom("fan-platform")))
                    .isInstanceOf(AccountAlreadyExistsException.class);

            verify(accountRepository, never()).findByEmail(eq(TenantId.CONSUMER_POOL), any());
            verify(accountRepository, never()).save(any());
            verify(profileRepository, never()).save(any());
            verify(membershipRepository, never()).insert(any());
            verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
        }

        @Test
        @DisplayName("🔴 AC-2: 동시 가입 경합(UNIQUE 위반)도 409 — 경합한 풀 계정을 돌려주지 않는다")
        void uniqueRace_refused_notLinkedToTheRacedPoolAccount() {
            given(accountRepository.save(any(Account.class)))
                    .willThrow(new DataIntegrityViolationException("uk_accounts_tenant_email"));
            lenient().when(accountRepository.findByEmail(TenantId.CONSUMER_POOL, EMAIL))
                    .thenReturn(Optional.of(account("acc-raced", TenantId.CONSUMER_POOL)));

            assertThatThrownBy(() -> useCase.execute(socialFrom("ecommerce")))
                    .isInstanceOf(AccountAlreadyExistsException.class);

            verify(accountRepository, never()).findByEmail(eq(TenantId.CONSUMER_POOL), any());
            verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
        }

        @Test
        @DisplayName("§ 2: 다른 소비자 사이트(스토어)에 같은 이메일의 사이트 계정 → 팬 소셜 풀 가입 거절 (공존 금지)")
        void siteAccountOnAnotherConsumerSite_refused() {
            given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(false);
            given(accountRepository.existsByEmail(ECOMMERCE, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> useCase.execute(socialFrom("fan-platform")))
                    .isInstanceOf(AccountAlreadyExistsException.class);

            verify(accountRepository, never()).save(any());
            verify(membershipRepository, never()).insert(any());
        }

        @Test
        @DisplayName("AC-3: 같은 사이트의 사이트 계정(078 이전) → 지금처럼 그 계정 (200) · 풀 경로를 타지 않는다")
        void sameSiteAccount_stillLinks_poolPathNotTaken() {
            given(accountRepository.findByEmail(FAN, EMAIL)).willReturn(Optional.of(account("acc-site", FAN)));

            SocialSignupResult result = useCase.execute(socialFrom("fan-platform"));

            assertThat(result.created()).isFalse();
            assertThat(result.accountId()).isEqualTo("acc-site");
            assertThat(result.tenantId()).isEqualTo("fan-platform");
            verify(accountRepository, never()).existsByEmail(any(), any());
            verify(membershipRepository, never()).insert(any());
        }

        @Test
        @DisplayName("D1: B2B 테넌트(wms) 소셜 가입은 풀로 가지 않는다 — 그 테넌트에 사이트 계정")
        void b2bTenant_staysPerTenant() {
            SocialSignupResult result = useCase.execute(socialFrom("wms"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(WMS);
            assertThat(result.tenantId()).isEqualTo("wms");
            verify(membershipRepository, never()).insert(any());
            verify(eventPublisher).publishAccountCreated(any(Account.class), eq("wms"), any());
        }
    }

    @Nested
    @DisplayName("플래그 꺼짐 (기본)")
    class FlagOff {

        @Test
        @DisplayName("플래그 꺼짐: 팬 소셜 가입은 이전처럼 fan-platform 사이트 계정 — 멤버십 없음, 다른 사이트 조회 없음")
        void consumerSiteSocialSignup_isThePrePoolSignup() {
            given(flag.isEnabled()).willReturn(false);

            SocialSignupResult result = useCase.execute(socialFrom("fan-platform"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(FAN);
            assertThat(result.tenantId()).isEqualTo("fan-platform");
            verify(membershipRepository, never()).insert(any());
            verify(tenantRepository, never()).findAllByTenantType(any());
            // TASK-BE-620 kept: the pool-email refusal is still asked on this path.
            verify(accountRepository).existsByEmail(TenantId.CONSUMER_POOL, EMAIL);
        }
    }
}
