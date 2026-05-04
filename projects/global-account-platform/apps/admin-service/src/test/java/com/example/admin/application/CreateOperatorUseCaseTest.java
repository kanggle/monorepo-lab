package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static com.example.admin.application.OperatorUseCaseTestSupport.newResolver;
import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static com.example.admin.application.OperatorUseCaseTestSupport.role;
import static com.example.admin.application.OperatorUseCaseTestSupport.setField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateOperatorUseCaseTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AdminOperatorRoleJpaRepository operatorRoleRepository;
    @Mock AdminRoleJpaRepository roleRepository;
    @Mock AdminActionAuditor auditor;
    @Mock PasswordHasher passwordHasher;
    @Mock OperatorLookupPort operatorLookupPort;

    CreateOperatorUseCase useCase;

    @BeforeEach
    void initUseCase() {
        OperatorRoleResolver resolver = newResolver(operatorRepository, roleRepository);
        useCase = new CreateOperatorUseCase(
                operatorRepository, operatorRoleRepository, auditor, passwordHasher,
                resolver, operatorLookupPort);

        // Default: actor is fan-platform (non-platform-scope)
        when(operatorLookupPort.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "actor-uuid", "fan-platform")));
    }

    @Test
    @DisplayName("운영자 생성 성공 시 비밀번호 해시 저장 + 감사 기록이 모두 수행된다")
    void createOperator_success_persists_hash_and_audits() {
        // TASK-BE-262: use per-tenant check (tenant_id, email)
        when(operatorRepository.existsByTenantIdAndEmail("fan-platform", "new@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(List.of("SUPER_ADMIN")))
                .thenReturn(List.of(role(1L, "SUPER_ADMIN")));
        when(passwordHasher.hash("StrongPass1!")).thenReturn("hash-value");
        when(operatorRepository.saveAndFlush(any(AdminOperatorJpaEntity.class)))
                .thenAnswer(inv -> {
                    AdminOperatorJpaEntity e = inv.getArgument(0);
                    setField(e, "id", 42L);
                    return e;
                });
        when(operatorRepository.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(operator(99L, "actor-uuid", "a@ex.com", "ACTIVE")));
        when(auditor.newAuditId()).thenReturn("audit-new");

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "new@example.com", "New Op", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "provisioning", "fan-platform");

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
        assertThat(result.auditId()).isEqualTo("audit-new");
        assertThat(result.tenantId()).isEqualTo("fan-platform");

        verify(passwordHasher, times(1)).hash("StrongPass1!");
        verify(operatorRoleRepository, times(1)).saveAll(anyList());

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_CREATE);
        assertThat(captor.getValue().targetType()).isEqualTo("OPERATOR");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(captor.getValue().reason()).isEqualTo("provisioning");
        assertThat(captor.getValue().targetTenantId()).isEqualTo("fan-platform");
    }

    @Test
    @DisplayName("이메일이 이미 존재하면 INSERT 전에 충돌 예외를 던진다")
    void createOperator_duplicate_email_throws_conflict_before_persist() {
        // TASK-BE-262: per-tenant check — same tenant, same email → conflict
        when(operatorRepository.existsByTenantIdAndEmail("fan-platform", "dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createOperator(
                "dup@example.com", "Dup", "StrongPass1!", List.of(), actor(), "reason", "fan-platform"))
                .isInstanceOf(OperatorEmailConflictException.class);

        verify(operatorRepository, never()).saveAndFlush(any());
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("알 수 없는 role 이름이 포함되면 RoleNotFoundException 으로 거부한다")
    void createOperator_unknown_role_throws_role_not_found() {
        when(operatorRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
        when(roleRepository.findByNameIn(List.of("DOES_NOT_EXIST"))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.createOperator(
                "ok@example.com", "Op", "StrongPass1!",
                List.of("DOES_NOT_EXIST"), actor(), "reason", "fan-platform"))
                .isInstanceOf(RoleNotFoundException.class);

        verify(operatorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("role 목록이 비어 있어도 생성은 허용된다")
    void createOperator_empty_roles_is_allowed() {
        when(operatorRepository.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
        when(passwordHasher.hash(anyString())).thenReturn("h");
        when(operatorRepository.saveAndFlush(any(AdminOperatorJpaEntity.class)))
                .thenAnswer(inv -> {
                    AdminOperatorJpaEntity e = inv.getArgument(0);
                    setField(e, "id", 55L);
                    return e;
                });
        when(operatorRepository.findByOperatorId("actor-uuid")).thenReturn(Optional.empty());
        when(auditor.newAuditId()).thenReturn("audit-2");

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "empty@example.com", "Empty", "StrongPass1!",
                List.of(), actor(), "reason", "fan-platform");

        assertThat(result.roles()).isEmpty();
        verify(operatorRoleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("TASK-BE-249: non-platform-scope actor cannot create platform-scope operator")
    void createOperator_non_platform_scope_actor_cannot_create_platform_scope_operator() {
        // actor is fan-platform (non-platform-scope) — set up by @BeforeEach

        assertThatThrownBy(() -> useCase.createOperator(
                "super@example.com", "Super", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "reason", "*"))
                .isInstanceOf(TenantScopeDeniedException.class)
                .hasMessageContaining("platform-scope");

        verify(operatorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("TASK-BE-249: platform-scope actor can create platform-scope operator")
    void createOperator_platform_scope_actor_can_create_platform_scope_operator() {
        // Override: actor is platform-scope (SUPER_ADMIN)
        when(operatorLookupPort.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "actor-uuid", "*")));

        // TASK-BE-262: per-tenant check — platform-scope tenant '*', email unique within '*'
        when(operatorRepository.existsByTenantIdAndEmail("*", "super@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(List.of("SUPER_ADMIN")))
                .thenReturn(List.of(role(1L, "SUPER_ADMIN")));
        when(passwordHasher.hash(anyString())).thenReturn("h");
        when(operatorRepository.saveAndFlush(any(AdminOperatorJpaEntity.class)))
                .thenAnswer(inv -> {
                    AdminOperatorJpaEntity e = inv.getArgument(0);
                    setField(e, "id", 100L);
                    return e;
                });
        when(operatorRepository.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(operator(99L, "actor-uuid", "a@ex.com", "ACTIVE")));
        when(auditor.newAuditId()).thenReturn("audit-sa");

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "super@example.com", "Super Admin", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "bootstrap", "*");

        assertThat(result.tenantId()).isEqualTo("*");
    }

    // ── TASK-BE-262: new tests ────────────────────────────────────────────────

    @Test
    @DisplayName("TASK-BE-262: 동일 이메일이 다른 tenantId에 존재하면 정상 생성 진행 (새 동작)")
    void createOperator_same_email_different_tenant_proceeds_to_create() {
        // Same email exists in "other-tenant", but we are creating in "fan-platform" → allowed
        when(operatorRepository.existsByTenantIdAndEmail("fan-platform", "shared@example.com")).thenReturn(false);
        when(roleRepository.findByNameIn(List.of("SUPPORT_LOCK")))
                .thenReturn(List.of(role(2L, "SUPPORT_LOCK")));
        when(passwordHasher.hash(anyString())).thenReturn("h");
        when(operatorRepository.saveAndFlush(any(AdminOperatorJpaEntity.class)))
                .thenAnswer(inv -> {
                    AdminOperatorJpaEntity e = inv.getArgument(0);
                    setField(e, "id", 77L);
                    return e;
                });
        when(operatorRepository.findByOperatorId("actor-uuid")).thenReturn(Optional.empty());
        when(auditor.newAuditId()).thenReturn("audit-cross");

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "shared@example.com", "Shared Email Op", "StrongPass1!",
                List.of("SUPPORT_LOCK"), actor(), "reason", "fan-platform");

        assertThat(result.email()).isEqualTo("shared@example.com");
        assertThat(result.tenantId()).isEqualTo("fan-platform");
        verify(operatorRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("TASK-BE-262: 동일 이메일 + 동일 tenantId → OperatorEmailConflictException (기존 동작 유지)")
    void createOperator_same_email_same_tenant_throws_conflict() {
        when(operatorRepository.existsByTenantIdAndEmail("fan-platform", "conflict@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createOperator(
                "conflict@example.com", "Conflict", "StrongPass1!",
                List.of(), actor(), "reason", "fan-platform"))
                .isInstanceOf(OperatorEmailConflictException.class);

        verify(operatorRepository, never()).saveAndFlush(any());
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("TASK-BE-262: TenantScopeDeniedException 발생 시 auditor.recordCrossTenantDenied() 호출")
    void createOperator_tenantScopeDenied_calls_auditor_record_cross_tenant_denied() {
        // actor is fan-platform (non-platform-scope) — attempting to create tenantId='*' operator → denied

        assertThatThrownBy(() -> useCase.createOperator(
                "super@example.com", "Super", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "reason", "*"))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(auditor).recordCrossTenantDenied(
                any(), anyString(),
                any(ActionCode.class), anyString(), anyString());
        verify(operatorRepository, never()).saveAndFlush(any());
    }
}
