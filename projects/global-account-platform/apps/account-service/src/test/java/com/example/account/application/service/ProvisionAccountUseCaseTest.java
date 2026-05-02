package com.example.account.application.service;

import com.example.account.application.command.ProvisionAccountCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountAlreadyExistsException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.exception.TenantSuspendedException;
import com.example.account.application.port.AuthServicePort;
import com.example.account.application.result.ProvisionAccountResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.profile.Profile;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.repository.ProfileRepository;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.status.AccountStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ProvisionAccountUseCase — TASK-BE-231")
class ProvisionAccountUseCaseTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private AccountEventPublisher eventPublisher;
    @Mock private AuthServicePort authServicePort;

    @InjectMocks private ProvisionAccountUseCase useCase;

    private static final String TENANT_ID = "wms";

    private Tenant activeTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private Tenant suspendedTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.SUSPENDED, Instant.now(), Instant.now());
    }

    private Account savedAccount() {
        return Account.reconstitute("acc-1", new TenantId(TENANT_ID), "user@example.com", null,
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
    }

    private Profile savedProfile() {
        return Profile.create("acc-1", "홍길동", "ko-KR", "Asia/Seoul");
    }

    private ProvisionAccountCommand command() {
        return new ProvisionAccountCommand(TENANT_ID, "user@example.com", "Password1!",
                "홍길동", "ko-KR", "Asia/Seoul", List.of("WAREHOUSE_ADMIN"), "sys-wms");
    }

    @Test
    @DisplayName("성공: Account + Profile + Role + Audit + Event 모두 생성된다")
    void execute_success_persistsAllEntitiesAndPublishesEvent() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.existsByEmail(any(TenantId.class), eq("user@example.com"))).willReturn(false);
        given(accountRepository.save(any(Account.class))).willReturn(savedAccount());
        given(profileRepository.save(any(Profile.class), any(TenantId.class))).willReturn(savedProfile());
        given(accountRoleRepository.save(any(AccountRole.class)))
                .willReturn(AccountRole.create(new TenantId(TENANT_ID), "acc-1", "WAREHOUSE_ADMIN", "sys-test"));
        given(historyRepository.save(any())).willReturn(null);

        ProvisionAccountResult result = useCase.execute(command());

        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roles()).containsExactly("WAREHOUSE_ADMIN");

        verify(accountRepository).save(any(Account.class));
        verify(profileRepository).save(any(Profile.class), any(TenantId.class));
        verify(accountRoleRepository).save(any(AccountRole.class));
        verify(historyRepository).save(any());
        verify(eventPublisher).publishAccountCreated(any(Account.class), any(String.class), any(String.class));
        verify(authServicePort).createCredential(any(), eq("user@example.com"), eq("Password1!"));
    }

    @Test
    @DisplayName("테넌트 없으면 TenantNotFoundException 발생")
    void execute_tenantNotFound_throwsTenantNotFoundException() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(TenantNotFoundException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("테넌트 SUSPENDED이면 TenantSuspendedException 발생")
    void execute_tenantSuspended_throwsTenantSuspendedException() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(suspendedTenant()));

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(TenantSuspendedException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 테넌트에 중복 이메일이면 AccountAlreadyExistsException 발생")
    void execute_duplicateEmail_throwsAccountAlreadyExistsException() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.existsByEmail(any(TenantId.class), eq("user@example.com"))).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(command()))
                .isInstanceOf(AccountAlreadyExistsException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("결과 DTO에 password_hash, deleted_at, email_hash 가 없다")
    void execute_result_excludesSensitiveFields() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.existsByEmail(any(TenantId.class), any())).willReturn(false);
        given(accountRepository.save(any())).willReturn(savedAccount());
        given(profileRepository.save(any(Profile.class), any(TenantId.class))).willReturn(savedProfile());
        given(accountRoleRepository.save(any())).willReturn(AccountRole.create(new TenantId(TENANT_ID), "acc-1", "WAREHOUSE_ADMIN", "sys-test"));
        given(historyRepository.save(any())).willReturn(null);

        ProvisionAccountResult result = useCase.execute(command());

        // ProvisionAccountResult only exposes accountId, tenantId, email, status, roles, createdAt
        assertThat(result.accountId()).isNotNull();
        assertThat(result.tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
    }
}
