package com.example.account.application.service;

import com.example.account.application.command.AddAccountRoleCommand;
import com.example.account.application.event.AccountEventPublisher;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.AccountRoleMutationResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.account.AccountRole;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.AccountRoleRepository;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AddAccountRoleUseCase — TASK-BE-255")
class AddAccountRoleUseCaseTest {

    private static final String TENANT_ID = "wms";
    private static final String ACCOUNT_ID = "acc-1";

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private AccountEventPublisher eventPublisher;

    @InjectMocks private AddAccountRoleUseCase useCase;

    private Tenant activeTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private Account targetAccount() {
        return Account.reconstitute(ACCOUNT_ID, new TenantId(TENANT_ID), "u@example.com", null,
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
    }

    private AddAccountRoleCommand command(String role) {
        return new AddAccountRoleCommand(TENANT_ID, ACCOUNT_ID, role, "sys-wms");
    }

    @Test
    @DisplayName("첫 추가 — 인서트 + audit + outbox event 발행")
    void execute_firstAdd_persistsAuditAndEmitsEvent() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.of(targetAccount()));
        given(accountRoleRepository.findByTenantIdAndAccountId(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(List.of());
        given(accountRoleRepository.addIfAbsent(any(AccountRole.class))).willReturn(true);

        AccountRoleMutationResult result = useCase.execute(command("INBOUND_OPERATOR"));

        assertThat(result.changed()).isTrue();
        assertThat(result.roles()).containsExactly("INBOUND_OPERATOR");
        verify(historyRepository).save(any());
        verify(eventPublisher).publishRolesChanged(any(), eq(TENANT_ID),
                eq(List.of()), eq(List.of("INBOUND_OPERATOR")), eq("sys-wms"),
                anyString(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("이미 존재하는 role 재추가 → no-op (audit/event 없음)")
    void execute_alreadyAssigned_isNoop() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.of(targetAccount()));
        given(accountRoleRepository.findByTenantIdAndAccountId(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(List.of(AccountRole.create(new TenantId(TENANT_ID), ACCOUNT_ID,
                        "INBOUND_OPERATOR", "sys-wms")));
        given(accountRoleRepository.addIfAbsent(any(AccountRole.class))).willReturn(false);

        AccountRoleMutationResult result = useCase.execute(command("INBOUND_OPERATOR"));

        assertThat(result.changed()).isFalse();
        assertThat(result.roles()).containsExactly("INBOUND_OPERATOR");
        verify(historyRepository, never()).save(any());
        verify(eventPublisher, never()).publishRolesChanged(
                any(), anyString(), any(), any(), any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("미등록 tenantId → TenantNotFoundException")
    void execute_unknownTenant_throws() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("ADMIN")))
                .isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    @DisplayName("cross-tenant — accountId 가 다른 테넌트 소속이면 AccountNotFoundException (404)")
    void execute_crossTenantAccount_throws() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("ADMIN")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("규격 위반 role 이름 → IllegalArgumentException (controller 레이어에서 400)")
    void execute_invalidRoleName_throws() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.of(targetAccount()));

        assertThatThrownBy(() -> useCase.execute(command("warehouse-admin")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
