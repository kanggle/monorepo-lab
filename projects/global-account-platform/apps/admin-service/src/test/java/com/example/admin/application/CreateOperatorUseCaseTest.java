package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.gap.security.password.PasswordHasher;
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

    CreateOperatorUseCase useCase;

    @org.junit.jupiter.api.BeforeEach
    void initUseCase() {
        OperatorRoleResolver resolver = newResolver(operatorRepository, roleRepository);
        useCase = new CreateOperatorUseCase(
                operatorRepository, operatorRoleRepository, auditor, passwordHasher, resolver);
    }

    @Test
    @DisplayName("운영자 생성 성공 시 비밀번호 해시 저장 + 감사 기록이 모두 수행된다")
    void createOperator_success_persists_hash_and_audits() {
        when(operatorRepository.existsByEmail("new@example.com")).thenReturn(false);
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
                List.of("SUPER_ADMIN"), actor(), "provisioning");

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
        assertThat(result.auditId()).isEqualTo("audit-new");

        verify(passwordHasher, times(1)).hash("StrongPass1!");
        verify(operatorRoleRepository, times(1)).saveAll(anyList());

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_CREATE);
        assertThat(captor.getValue().targetType()).isEqualTo("OPERATOR");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(captor.getValue().reason()).isEqualTo("provisioning");
    }

    @Test
    @DisplayName("이메일이 이미 존재하면 INSERT 전에 충돌 예외를 던진다")
    void createOperator_duplicate_email_throws_conflict_before_persist() {
        when(operatorRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createOperator(
                "dup@example.com", "Dup", "StrongPass1!", List.of(), actor(), "reason"))
                .isInstanceOf(OperatorEmailConflictException.class);

        verify(operatorRepository, never()).saveAndFlush(any());
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("알 수 없는 role 이름이 포함되면 RoleNotFoundException 으로 거부한다")
    void createOperator_unknown_role_throws_role_not_found() {
        when(operatorRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByNameIn(List.of("DOES_NOT_EXIST"))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.createOperator(
                "ok@example.com", "Op", "StrongPass1!",
                List.of("DOES_NOT_EXIST"), actor(), "reason"))
                .isInstanceOf(RoleNotFoundException.class);

        verify(operatorRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("role 목록이 비어 있어도 생성은 허용된다")
    void createOperator_empty_roles_is_allowed() {
        when(operatorRepository.existsByEmail(anyString())).thenReturn(false);
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
                List.of(), actor(), "reason");

        assertThat(result.roles()).isEmpty();
        verify(operatorRoleRepository, never()).saveAll(anyList());
    }
}
