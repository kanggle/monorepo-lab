package com.example.admin.application;

import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatchOperatorRoleUseCaseTest {

    @Mock AdminOperatorJpaRepository operatorRepository;
    @Mock AdminOperatorRoleJpaRepository operatorRoleRepository;
    @Mock AdminRoleJpaRepository roleRepository;
    @Mock AdminActionAuditor auditor;
    @Mock CachingPermissionEvaluator cachingPermissionEvaluator;

    PatchOperatorRoleUseCase useCase;

    @BeforeEach
    void initUseCase() {
        OperatorRoleResolver resolver = newResolver(operatorRepository, roleRepository);
        useCase = new PatchOperatorRoleUseCase(
                operatorRepository, operatorRoleRepository, auditor, cachingPermissionEvaluator, resolver);
    }

    @Test
    @DisplayName("role 패치 성공 시 기존 role 을 모두 교체하고 권한 캐시를 무효화한다")
    void patchRoles_replaces_all_and_invalidates_cache() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));
        when(roleRepository.findByNameIn(List.of("SUPPORT_READONLY", "SECURITY_ANALYST")))
                .thenReturn(List.of(
                        role(2L, "SUPPORT_READONLY"),
                        role(4L, "SECURITY_ANALYST")));
        when(operatorRepository.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(operator(99L, "actor-uuid", "a@ex.com", "ACTIVE")));
        when(auditor.newAuditId()).thenReturn("audit-patch");

        PatchOperatorRoleUseCase.PatchRolesResult result = useCase.patchRoles(
                "target-uuid",
                List.of("SUPPORT_READONLY", "SECURITY_ANALYST"),
                actor(),
                "quarterly rotation");

        assertThat(result.roles()).containsExactly("SUPPORT_READONLY", "SECURITY_ANALYST");
        verify(operatorRoleRepository).deleteByOperatorId(77L);
        verify(operatorRoleRepository).saveAll(anyList());
        verify(cachingPermissionEvaluator).invalidate("target-uuid");
    }

    @Test
    @DisplayName("빈 role 배열도 허용되며 권한 캐시는 여전히 무효화된다")
    void patchRoles_empty_array_allowed_still_invalidates_cache() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));
        when(operatorRepository.findByOperatorId("actor-uuid")).thenReturn(Optional.empty());
        when(auditor.newAuditId()).thenReturn("audit-empty");

        PatchOperatorRoleUseCase.PatchRolesResult result = useCase.patchRoles(
                "target-uuid", List.of(), actor(), "demotion");

        assertThat(result.roles()).isEmpty();
        verify(operatorRoleRepository).deleteByOperatorId(77L);
        verify(operatorRoleRepository, never()).saveAll(anyList());
        verify(cachingPermissionEvaluator).invalidate("target-uuid");
    }

    @Test
    @DisplayName("알 수 없는 role 이 포함되면 예외를 던지고 기존 role 삭제 단계로 진행하지 않는다")
    void patchRoles_unknown_role_throws_and_skips_delete() {
        AdminOperatorJpaEntity target = operator(77L, "target-uuid", "t@ex.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("target-uuid")).thenReturn(Optional.of(target));
        when(roleRepository.findByNameIn(List.of("GHOST"))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.patchRoles(
                "target-uuid", List.of("GHOST"), actor(), "reason"))
                .isInstanceOf(RoleNotFoundException.class);

        verify(operatorRoleRepository, never()).deleteByOperatorId(anyLong());
        verify(cachingPermissionEvaluator, never()).invalidate(anyString());
    }

    @Test
    @DisplayName("대상 운영자가 존재하지 않으면 OperatorNotFoundException 을 던진다")
    void patchRoles_missing_operator_throws_not_found() {
        when(operatorRepository.findByOperatorId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.patchRoles(
                "ghost", List.of(), actor(), "reason"))
                .isInstanceOf(OperatorNotFoundException.class);
    }
}
