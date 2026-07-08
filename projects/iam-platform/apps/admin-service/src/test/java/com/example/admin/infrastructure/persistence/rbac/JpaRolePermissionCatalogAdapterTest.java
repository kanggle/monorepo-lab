package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.application.port.RolePermissionCatalogPort.RoleWithPermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-486 — unit coverage for the role→permission assembly logic in
 * {@link JpaRolePermissionCatalogAdapter}: stable role-id order preserved,
 * per-role permission keys grouped + sorted ascending, roles with no bindings
 * carry an empty list, and an empty catalog returns an empty list.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class JpaRolePermissionCatalogAdapterTest {

    @Mock AdminRoleJpaRepository roleRepository;
    @Mock AdminRolePermissionJpaRepository rolePermissionRepository;

    @InjectMocks JpaRolePermissionCatalogAdapter adapter;

    @Test
    void assembles_roles_in_id_order_with_sorted_permission_keys() {
        when(roleRepository.findAll(any(Sort.class))).thenReturn(List.of(
                role(1L, "SUPER_ADMIN", "Full platform administrator"),
                role(2L, "SUPPORT_READONLY", "CS L1")));
        // Deliberately unsorted keys + interleaved role ids to prove grouping + sorting.
        when(rolePermissionRepository.findByRoleIdIn(anyCollection())).thenReturn(List.of(
                binding(1L, "operator.manage"),
                binding(2L, "security.event.read"),
                binding(1L, "account.lock"),
                binding(2L, "audit.read")));

        List<RoleWithPermissions> result = adapter.findAllRolesWithPermissions();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("SUPER_ADMIN");
        assertThat(result.get(0).permissionKeys())
                .containsExactly("account.lock", "operator.manage"); // sorted asc
        assertThat(result.get(1).id()).isEqualTo(2L);
        assertThat(result.get(1).permissionKeys())
                .containsExactly("audit.read", "security.event.read"); // sorted asc
    }

    @Test
    void role_without_bindings_carries_empty_permission_list() {
        when(roleRepository.findAll(any(Sort.class))).thenReturn(List.of(
                role(9L, "EMPTY_ROLE", "no perms")));
        when(rolePermissionRepository.findByRoleIdIn(anyCollection())).thenReturn(List.of());

        List<RoleWithPermissions> result = adapter.findAllRolesWithPermissions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).permissionKeys()).isEmpty();
    }

    @Test
    void empty_role_catalog_returns_empty_list() {
        when(roleRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(adapter.findAllRolesWithPermissions()).isEmpty();
    }

    // ----- entity fixtures (entities have protected no-arg ctors / no public setters) -----

    private static AdminRoleJpaEntity role(long id, String name, String description) {
        try {
            Constructor<AdminRoleJpaEntity> ctor = AdminRoleJpaEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AdminRoleJpaEntity e = ctor.newInstance();
            setField(e, "id", id);
            setField(e, "name", name);
            setField(e, "description", description);
            setField(e, "require2fa", false);
            return e;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static AdminRolePermissionJpaEntity binding(long roleId, String permissionKey) {
        try {
            Constructor<AdminRolePermissionJpaEntity> ctor =
                    AdminRolePermissionJpaEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AdminRolePermissionJpaEntity e = ctor.newInstance();
            setField(e, "roleId", roleId);
            setField(e, "permissionKey", permissionKey);
            return e;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void setField(Object target, String field, Object value)
            throws ReflectiveOperationException {
        var f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
