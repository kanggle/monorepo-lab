package com.example.account.application.service;

import com.example.account.application.command.SignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.domain.account.Account;
import com.example.account.domain.consumerpool.ConsumerSiteMembership;
import com.example.account.domain.consumerpool.ConsumerSiteMembershipStatus;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.ConsumerSiteMembershipRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
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
 * TASK-BE-614 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 2·3·6) — the signup path with
 * the real {@link ConsumerAccountPool} rules over mocked ports. The DB-level proof (rows, FK,
 * the event row, the lookups) is {@code ConsumerPoolSignupIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignupUseCase — 소비자 계정 풀 (TASK-BE-614)")
class SignupUseCaseConsumerPoolTest {

    private static final TenantId FAN = TenantId.FAN_PLATFORM;
    private static final TenantId ECOMMERCE = new TenantId("ecommerce");
    private static final TenantId WMS = new TenantId("wms");
    private static final String EMAIL = "shopper@example.com";

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private AuthServicePort authServicePort;
    @Mock private AccountIdentityProvisioner accountIdentityProvisioner;
    @Mock private TenantRepository tenantRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private ConsumerPoolFlag flag;

    private SignupUseCase signupUseCase;

    private static final Map<TenantId, TenantType> TENANTS = Map.of(
            FAN, TenantType.B2C_CONSUMER,
            ECOMMERCE, TenantType.B2C_CONSUMER,
            TenantId.CONSUMER_POOL, TenantType.B2C_CONSUMER,
            WMS, TenantType.B2B_ENTERPRISE);

    private static Tenant tenant(TenantId id) {
        return Tenant.reconstitute(id, id.value(), TENANTS.get(id), TenantStatus.ACTIVE,
                Instant.now(), Instant.now());
    }

    @BeforeEach
    void setUp() {
        ConsumerAccountPool pool = new ConsumerAccountPool(
                flag, tenantRepository, accountRepository, membershipRepository);
        signupUseCase = new SignupUseCase(accountRepository, profileRepository, eventPublisher,
                authServicePort, accountIdentityProvisioner, new ActiveTenantGuard(tenantRepository), pool);
        lenient().when(tenantRepository.findById(any(TenantId.class)))
                .thenAnswer(inv -> Optional.ofNullable(TENANTS.containsKey(inv.<TenantId>getArgument(0))
                        ? tenant(inv.getArgument(0)) : null));
        lenient().when(tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER))
                .thenReturn(List.of(tenant(TenantId.CONSUMER_POOL), tenant(ECOMMERCE), tenant(FAN)));
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(accountIdentityProvisioner.mintIdentity(anyString(), eq(EMAIL))).thenReturn("idy-1");
    }

    private static SignupCommand signupFrom(String tenantId) {
        return new SignupCommand(EMAIL, "Password1!", "Shopper", "ko-KR", "Asia/Seoul", tenantId);
    }

    @Nested
    @DisplayName("플래그 켜짐")
    class FlagOn {

        @BeforeEach
        void on() {
            lenient().when(flag.isEnabled()).thenReturn(true);
        }

        @Test
        @DisplayName("AC-3/AC-10: 스토어 가입 → 계정·자격은 consumer-pool, 멤버십은 ecommerce, account.created 는 ecommerce 로 1회")
        void consumerSiteSignup_bornInPool_membershipAndEventCarryTheSite() {
            signupUseCase.execute(signupFrom("ecommerce"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            Account account = saved.getValue();
            assertThat(account.getTenantId()).isEqualTo(TenantId.CONSUMER_POOL);

            ArgumentCaptor<ConsumerSiteMembership> membership =
                    ArgumentCaptor.forClass(ConsumerSiteMembership.class);
            verify(membershipRepository).insert(membership.capture());
            assertThat(membership.getValue().getAccountId()).isEqualTo(account.getId());
            assertThat(membership.getValue().getSiteTenantId()).isEqualTo(ECOMMERCE);
            assertThat(membership.getValue().getStatus()).isEqualTo(ConsumerSiteMembershipStatus.ACTIVE);
            assertThat(membership.getValue().getConsentedAt()).isEqualTo(account.getCreatedAt());

            // pool credential row (auth-service data-model.md § credentials — 소비자 계정 풀)
            verify(authServicePort).createCredential(eq(account.getId()), eq(EMAIL), eq("Password1!"),
                    eq("consumer-pool"), eq("idy-1"));
            // the central identity is minted in the pool tenant (the account's tenant)
            verify(accountIdentityProvisioner).mintIdentity("consumer-pool", EMAIL);
            verify(accountRepository).assignIdentityId(TenantId.CONSUMER_POOL, account.getId(), "idy-1");

            // § 6: the site, never the storage value
            ArgumentCaptor<String> eventTenant = ArgumentCaptor.forClass(String.class);
            verify(eventPublisher).publishAccountCreated(eq(account), eventTenant.capture(), any());
            assertThat(eventTenant.getValue()).isEqualTo("ecommerce").isNotEqualTo("consumer-pool");
        }

        @Test
        @DisplayName("§ 2 / AC-3: 같은 이메일의 팬 사이트 계정이 있으면 스토어 풀 가입은 거절 — 묶지도, 공존시키지도 않는다")
        void emailWithSiteAccountElsewhere_refusedWithDuplicateAnswer() {
            given(accountRepository.existsByEmail(ECOMMERCE, EMAIL)).willReturn(false);
            given(accountRepository.existsByEmail(FAN, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> signupUseCase.execute(signupFrom("ecommerce")))
                    .isInstanceOf(AccountAlreadyExistsException.class);

            verify(accountRepository, never()).save(any(Account.class));
            verify(membershipRepository, never()).insert(any());
            verify(authServicePort, never()).createCredential(any(), any(), any(), any(), any());
            verify(eventPublisher, never()).publishAccountCreated(any(), any(), any());
        }

        @Test
        @DisplayName("§ 2: 이미 풀 계정이 있는 이메일 → 같은 중복 응답")
        void emailAlreadyInPool_refused() {
            given(accountRepository.existsByEmail(ECOMMERCE, EMAIL)).willReturn(false);
            given(accountRepository.existsByEmail(FAN, EMAIL)).willReturn(false);
            given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> signupUseCase.execute(signupFrom("fan-platform")))
                    .isInstanceOf(AccountAlreadyExistsException.class);

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("거절 술어는 consumer-pool 자신을 «사이트» 로 세지 않는다 — 사이트는 B2C 중 풀 제외")
        void siteAccountCheck_skipsThePoolTenant() {
            signupUseCase.execute(signupFrom("fan-platform"));

            verify(accountRepository).existsByEmail(FAN, EMAIL);
            verify(accountRepository).existsByEmail(ECOMMERCE, EMAIL);
            // the pool is asked exactly once — by the pool-duplicate check, not the site loop
            verify(accountRepository).existsByEmail(TenantId.CONSUMER_POOL, EMAIL);
        }

        @Test
        @DisplayName("D1: 소비자 사이트가 아닌 테넌트(wms, B2B) 가입은 풀로 가지 않는다")
        void nonConsumerTenant_keepsPerTenantSignup() {
            signupUseCase.execute(signupFrom("wms"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(WMS);
            verify(membershipRepository, never()).insert(any());
            verify(authServicePort).createCredential(any(), eq(EMAIL), any(), eq("wms"), any());
            verify(eventPublisher).publishAccountCreated(any(Account.class), eq("wms"), any());
        }
    }

    @Nested
    @DisplayName("플래그 꺼짐 (기본) — AC-8")
    class FlagOff {

        @Test
        @DisplayName("AC-8: 스토어 가입은 이전처럼 ecommerce 테넌트에 — 멤버십 없음, 다른 사이트 조회 없음")
        void consumerSiteSignup_isThePrePoolSignup() {
            given(flag.isEnabled()).willReturn(false);

            signupUseCase.execute(signupFrom("ecommerce"));

            ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(ECOMMERCE);
            verify(membershipRepository, never()).insert(any());
            verify(tenantRepository, never()).findAllByTenantType(any());
            verify(accountRepository, never()).existsByEmail(eq(FAN), any());
            verify(authServicePort).createCredential(any(), eq(EMAIL), any(), eq("ecommerce"), any());
            verify(eventPublisher).publishAccountCreated(any(Account.class), eq("ecommerce"), any());
        }
    }

    @Test
    @DisplayName("consumer-pool 을 직접 지명한 가입은 플래그와 무관하게 TenantNotFound — 행이 생기기 전과 같은 응답")
    void namingThePoolTenant_isNotFound_regardlessOfFlag() {
        assertThatThrownBy(() -> signupUseCase.execute(signupFrom("consumer-pool")))
                .isInstanceOf(TenantNotFoundException.class);

        verify(accountRepository, never()).save(any(Account.class));
        verify(tenantRepository, never()).findById(TenantId.CONSUMER_POOL);
    }
}
