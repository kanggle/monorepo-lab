package com.example.admin.application;

import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.newResolver;
import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static com.example.admin.application.OperatorUseCaseTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorRoleResolver 단위 테스트")
class OperatorRoleResolverTest {

    @Mock
    private AdminOperatorJpaRepository operatorRepository;
    @Mock
    private AdminRoleJpaRepository roleRepository;

    private OperatorRoleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = newResolver(operatorRepository, roleRepository);
    }

    // ── resolveRoles ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveRoles(null) → empty map")
    void resolveRoles_null_returnsEmptyMap() {
        assertThat(resolver.resolveRoles(null)).isEmpty();
    }

    @Test
    @DisplayName("resolveRoles([]) → empty map")
    void resolveRoles_empty_returnsEmptyMap() {
        assertThat(resolver.resolveRoles(List.of())).isEmpty();
    }

    @Test
    @DisplayName("resolveRoles — 알려진 role 이름 → populated map")
    void resolveRoles_knownRole_returnsPopulatedMap() {
        AdminRoleJpaEntity superAdmin = role(1L, "SUPER_ADMIN");
        when(roleRepository.findByNameIn(anyList())).thenReturn(List.of(superAdmin));

        Map<String, AdminRoleJpaEntity> result = resolver.resolveRoles(List.of("SUPER_ADMIN"));

        assertThat(result).hasSize(1).containsKey("SUPER_ADMIN");
        assertThat(result.get("SUPER_ADMIN").getName()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("resolveRoles — 알 수 없는 role → RoleNotFoundException")
    void resolveRoles_unknownRole_throwsRoleNotFoundException() {
        when(roleRepository.findByNameIn(anyList())).thenReturn(List.of());

        assertThatThrownBy(() -> resolver.resolveRoles(List.of("UNKNOWN_ROLE")))
                .isInstanceOf(RoleNotFoundException.class);
    }

    @Test
    @DisplayName("resolveRoles — null·공백 항목은 스킵")
    void resolveRoles_blankAndNullEntries_skipped() {
        AdminRoleJpaEntity viewer = role(2L, "VIEWER");
        when(roleRepository.findByNameIn(anyList())).thenReturn(List.of(viewer));

        Map<String, AdminRoleJpaEntity> result = resolver.resolveRoles(
                Arrays.asList(null, "  ", "VIEWER"));

        assertThat(result).hasSize(1).containsKey("VIEWER");
    }

    @Test
    @DisplayName("resolveRoles — 중복 이름 → 첫 번째 occurrence 유지, 크기=1")
    void resolveRoles_duplicateNames_deduplicates() {
        AdminRoleJpaEntity superAdmin = role(1L, "SUPER_ADMIN");
        when(roleRepository.findByNameIn(anyList())).thenReturn(List.of(superAdmin));

        Map<String, AdminRoleJpaEntity> result = resolver.resolveRoles(
                List.of("SUPER_ADMIN", "SUPER_ADMIN"));

        assertThat(result).hasSize(1).containsKey("SUPER_ADMIN");
    }

    @Test
    @DisplayName("resolveRoles — 두 role 모두 존재 → 순서 유지 반환")
    void resolveRoles_multipleKnownRoles_returnsAllInOrder() {
        AdminRoleJpaEntity r1 = role(1L, "SUPER_ADMIN");
        AdminRoleJpaEntity r2 = role(2L, "VIEWER");
        when(roleRepository.findByNameIn(anyList())).thenReturn(List.of(r1, r2));

        Map<String, AdminRoleJpaEntity> result = resolver.resolveRoles(
                List.of("SUPER_ADMIN", "VIEWER"));

        assertThat(result).hasSize(2).containsKeys("SUPER_ADMIN", "VIEWER");
    }

    // ── resolveActorInternalId ─────────────────────────────────────────────────

    @Test
    @DisplayName("resolveActorInternalId(null actor) → null")
    void resolveActorInternalId_nullActor_returnsNull() {
        assertThat(resolver.resolveActorInternalId(null)).isNull();
    }

    @Test
    @DisplayName("resolveActorInternalId(operatorId=null) → null")
    void resolveActorInternalId_nullOperatorId_returnsNull() {
        OperatorContext actor = new OperatorContext(null, "jti-1");

        assertThat(resolver.resolveActorInternalId(actor)).isNull();
    }

    @Test
    @DisplayName("resolveActorInternalId — 레포지토리에 없음 → null")
    void resolveActorInternalId_notFoundInRepo_returnsNull() {
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.empty());

        OperatorContext actor = new OperatorContext("op-uuid", "jti-1");

        assertThat(resolver.resolveActorInternalId(actor)).isNull();
    }

    @Test
    @DisplayName("resolveActorInternalId — 레포지토리에 존재 → internal id 반환")
    void resolveActorInternalId_found_returnsId() {
        AdminOperatorJpaEntity entity = operator(42L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorRepository.findByOperatorId("op-uuid")).thenReturn(Optional.of(entity));

        OperatorContext actor = new OperatorContext("op-uuid", "jti-1");

        assertThat(resolver.resolveActorInternalId(actor)).isEqualTo(42L);
    }

    // ── normalizeReason ────────────────────────────────────────────────────────

    @Test
    @DisplayName("normalizeReason(null) → '<not_provided>'")
    void normalizeReason_null_returnsPlaceholder() {
        assertThat(OperatorRoleResolver.normalizeReason(null))
                .isEqualTo(OperatorRoleResolver.REASON_NOT_PROVIDED);
    }

    @Test
    @DisplayName("normalizeReason(공백) → '<not_provided>'")
    void normalizeReason_blank_returnsPlaceholder() {
        assertThat(OperatorRoleResolver.normalizeReason("   "))
                .isEqualTo(OperatorRoleResolver.REASON_NOT_PROVIDED);
    }

    @Test
    @DisplayName("normalizeReason(값 있음) → 그대로 반환")
    void normalizeReason_givenValue_returnsAsIs() {
        assertThat(OperatorRoleResolver.normalizeReason("보안 정책 위반")).isEqualTo("보안 정책 위반");
    }
}
