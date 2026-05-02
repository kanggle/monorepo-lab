package com.example.account.application.service;

import com.example.account.application.command.RemoveAccountRoleCommand;
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
@DisplayName("RemoveAccountRoleUseCase — TASK-BE-255")
class RemoveAccountRoleUseCaseTest {

    private static final String TENANT_ID = "wms";
    private static final String ACCOUNT_ID = "acc-1";

    @Mock private TenantRepository tenantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountRoleRepository accountRoleRepository;
    @Mock private AccountStatusHistoryRepository historyRepository;
    @Mock private AccountEventPublisher eventPublisher;

    @InjectMocks private RemoveAccountRoleUseCase useCase;

    private Tenant activeTenant() {
        return Tenant.reconstitute(new TenantId(TENANT_ID), "WMS", TenantType.B2B_ENTERPRISE,
                TenantStatus.ACTIVE, Instant.now(), Instant.now());
    }

    private Account targetAccount() {
        return Account.reconstitute(ACCOUNT_ID, new TenantId(TENANT_ID), "u@example.com", null,
                AccountStatus.ACTIVE, Instant.now(), Instant.now(), null, null, null, 0);
    }

    private RemoveAccountRoleCommand command(String role) {
        return new RemoveAccountRoleCommand(TENANT_ID, ACCOUNT_ID, role, "sys-wms");
    }

    @Test
    @DisplayName("기존 role 제거 — 삭제 + audit + outbox event 발행")
    void execute_existingRole_persistsAuditAndEmitsEvent() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.of(targetAccount()));
        given(accountRoleRepository.findByTenantIdAndAccountId(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(List.of(
                        AccountRole.create(new TenantId(TENANT_ID), ACCOUNT_ID, "WAREHOUSE_ADMIN", "sys-wms"),
                        AccountRole.create(new TenantId(TENANT_ID), ACCOUNT_ID, "INBOUND_OPERATOR", "sys-wms")
                ));
        given(accountRoleRepository.removeIfPresent(any(TenantId.class), eq(ACCOUNT_ID), eq("INBOUND_OPERATOR")))
                .willReturn(true);

        AccountRoleMutationResult result = useCase.execute(command("INBOUND_OPERATOR"));

        assertThat(result.changed()).isTrue();
        assertThat(result.roles()).containsExactly("WAREHOUSE_ADMIN");
        verify(historyRepository).save(any());
        verify(eventPublisher).publishRolesChanged(any(), eq(TENANT_ID),
                any(), any(), eq("sys-wms"),
                anyString(), anyString(), any(Instant.class));
    }

    @Test
    @DisplayName("미할당 role 제거 시도 → no-op (audit/event 없음)")
    void execute_roleNotAssigned_isNoop() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.of(targetAccount()));
        given(accountRoleRepository.findByTenantIdAndAccountId(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(List.of(
                        AccountRole.create(new TenantId(TENANT_ID), ACCOUNT_ID, "WAREHOUSE_ADMIN", "sys-wms")
                ));
        given(accountRoleRepository.removeIfPresent(any(TenantId.class), eq(ACCOUNT_ID), eq("INBOUND_OPERATOR")))
                .willReturn(false);

        AccountRoleMutationResult result = useCase.execute(command("INBOUND_OPERATOR"));

        assertThat(result.changed()).isFalse();
        assertThat(result.roles()).containsExactly("WAREHOUSE_ADMIN");
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
    @DisplayName("cross-tenant — accountId 가 다른 테넌트면 AccountNotFoundException")
    void execute_crossTenantAccount_throws() {
        given(tenantRepository.findById(any(TenantId.class))).willReturn(Optional.of(activeTenant()));
        given(accountRepository.findById(any(TenantId.class), eq(ACCOUNT_ID)))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command("ADMIN")))
                .isInstanceOf(AccountNotFoundException.class);
    }
}
