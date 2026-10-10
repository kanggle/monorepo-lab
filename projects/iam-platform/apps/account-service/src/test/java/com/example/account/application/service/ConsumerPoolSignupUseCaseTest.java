package com.example.account.application.service;

import com.example.account.application.command.ConsumerPoolSignupCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.ConsumerPoolDisabledException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.port.ConsumerPoolFlag;
import com.example.account.application.result.SignupResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.PasswordPolicyViolationException;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-MONO-772 S3 (auth-to-account.md § {@code POST /internal/consumer-pool/signups}) — the site-less pool signup
 * with the real {@link ConsumerAccountPool} rules over mocked ports.
 *
 * <p>🔴 The point of the endpoint: a pool account is created and 🔴 NO site membership and NO {@code account.created}
 * event — an employee is not made a store / fan member by accepting a company invitation (772 AC-0 F8). The membership
 * repository and the event publisher are real mocks here, so «never called» is the assertion, not an absence.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumerPoolSignupUseCase — 사이트 없는 풀 가입 (TASK-MONO-772 S3)")
class ConsumerPoolSignupUseCaseTest {

    private static final String EMAIL = "invitee@example.com";
    private static final TenantId ECOMMERCE = new TenantId("ecommerce");

    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AuthServicePort authServicePort;
    @Mock private AccountIdentityProvisioner accountIdentityProvisioner;
    @Mock private TenantRepository tenantRepository;
    @Mock private ConsumerSiteMembershipRepository membershipRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private ConsumerPoolFlag flag;

    private ConsumerPoolSignupUseCase useCase;

    @BeforeEach
    void setUp() {
        ConsumerAccountPool pool = new ConsumerAccountPool(flag, tenantRepository, accountRepository, membershipRepository);
        useCase = new ConsumerPoolSignupUseCase(accountRepository, profileRepository, authServicePort,
                accountIdentityProvisioner, pool, flag);
        lenient().when(tenantRepository.findAllByTenantType(TenantType.B2C_CONSUMER)).thenReturn(List.of(
                Tenant.reconstitute(TenantId.CONSUMER_POOL, "pool", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE,
                        Instant.now(), Instant.now()),
                Tenant.reconstitute(ECOMMERCE, "store", TenantType.B2C_CONSUMER, TenantStatus.ACTIVE,
                        Instant.now(), Instant.now())));
        lenient().when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ConsumerPoolSignupCommand command() {
        return new ConsumerPoolSignupCommand(EMAIL, "Password1!", "피초대자", "ko-KR", "Asia/Seoul");
    }

    @Test
    @DisplayName("🔴 풀 계정 + 프로필 + 풀 자격 · 사이트 멤버십 없음 · account.created 없음")
    void createsPoolAccountOnly() {
        given(flag.isEnabled()).willReturn(true);
        given(accountIdentityProvisioner.mintIdentity("consumer-pool", EMAIL)).willReturn("idy-1");

        SignupResult result = useCase.execute(command());

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(TenantId.CONSUMER_POOL);
        assertThat(result.accountId()).isEqualTo(saved.getValue().getId());
        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(profileRepository).save(any());
        verify(accountRepository).assignIdentityId(TenantId.CONSUMER_POOL, saved.getValue().getId(), "idy-1");
        verify(authServicePort).createCredential(eq(saved.getValue().getId()), eq(EMAIL), eq("Password1!"),
                eq("consumer-pool"), eq("idy-1"));

        verify(membershipRepository, never()).insert(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("같은 이메일의 풀 계정 → 409 ACCOUNT_ALREADY_EXISTS · 아무것도 안 만든다")
    void poolDuplicate_409() {
        given(flag.isEnabled()).willReturn(true);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(AccountAlreadyExistsException.class);
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("§ 2 공존 금지: 같은 이메일의 소비자 사이트 계정 → 소비자 가입과 같은 409 · 아무것도 안 만든다")
    void siteAccountCoexistence_refused() {
        given(flag.isEnabled()).willReturn(true);
        given(accountRepository.existsByEmail(TenantId.CONSUMER_POOL, EMAIL)).willReturn(false);
        given(accountRepository.existsByEmail(ECOMMERCE, EMAIL)).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(AccountAlreadyExistsException.class);
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(authServicePort);
    }

    @Test
    @DisplayName("풀 플래그 꺼짐 → 409 CONSUMER_POOL_DISABLED · 조회조차 없음")
    void poolDisabled_409() {
        given(flag.isEnabled()).willReturn(false);

        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(ConsumerPoolDisabledException.class);
        verifyNoInteractions(accountRepository, authServicePort);
    }

    @Test
    @DisplayName("약한 비밀번호 → 422 (PasswordPolicy) · 계정 안 만든다")
    void weakPassword_refused() {
        given(flag.isEnabled()).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(
                new ConsumerPoolSignupCommand(EMAIL, "password", null, null, null)))
                .isInstanceOf(PasswordPolicyViolationException.class);
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("자격 생성이 경합 중복 → 409 (가입 전체 실패)")
    void credentialConflict_409() {
        given(flag.isEnabled()).willReturn(true);
        given(accountIdentityProvisioner.mintIdentity(anyString(), anyString())).willReturn(null);
        org.mockito.BDDMockito.willThrow(new AuthServicePort.CredentialAlreadyExistsConflict("acc-x"))
                .given(authServicePort).createCredential(anyString(), anyString(), anyString(), anyString(), any());

        assertThatThrownBy(() -> useCase.execute(command())).isInstanceOf(AccountAlreadyExistsException.class);
    }

    @Test
    @DisplayName("🔴 R4: 명령 toString 은 비밀번호 · 이메일을 찍지 않는다")
    void commandToString_redacts() {
        assertThat(command().toString()).doesNotContain("Password1!").doesNotContain(EMAIL)
                .contains("<redacted>").contains("<masked>");
    }
}
