package com.example.admin.infrastructure.persistence.rbac;

import com.example.admin.domain.rbac.AdminOperator;
import com.example.admin.domain.rbac.Permission;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PermissionEvaluatorImplUnitTest {

    @Mock AdminOperatorJpaRepository operators;
    @Mock AdminOperatorRoleJpaRepository operatorRoles;
    @Mock AdminRolePermissionJpaRepository rolePermissions;

    @InjectMocks PermissionEvaluatorImpl evaluator;

    private AdminOperatorJpaEntity operator(Long internalId, String externalUuid, String status) {
        AdminOperatorJpaEntity e = new AdminOperatorJpaEntity() {};
        try {
            set(e, "id", internalId);
            set(e, "operatorId", externalUuid);
            set(e, "status", status);
            set(e, "email", "x@example.com");
            set(e, "passwordHash", "h");
            set(e, "displayName", "n");
            set(e, "createdAt", Instant.now());
            set(e, "updatedAt", Instant.now());
        } catch (ReflectiveOperationException ex) { throw new IllegalStateException(ex); }
        return e;
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        var f = AdminOperatorJpaEntity.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private AdminOperatorRoleJpaEntity roleBinding(Long op, Long role) {
        return AdminOperatorRoleJpaEntity.create(op, role, Instant.now(), null);
    }

    @Test
    void hasPermission_returns_true_for_union_across_roles() {
        when(operators.findByOperatorId("op-1"))
                .thenReturn(Optional.of(operator(100L, "op-1", AdminOperator.Status.ACTIVE.name())));
        when(operatorRoles.findByOperatorId(100L)).thenReturn(
                List.of(roleBinding(100L, 1L), roleBinding(100L, 2L)));
        when(rolePermissions.findPermissionKeysByRoleIds(anyCollection()))
                .thenReturn(List.of(Permission.AUDIT_READ, Permission.ACCOUNT_LOCK));

        assertThat(evaluator.hasPermission("op-1", Permission.AUDIT_READ)).isTrue();
    }

    @Test
    void hasPermission_returns_false_for_missing_operator() {
        when(operators.findByOperatorId("ghost")).thenReturn(Optional.empty());

        assertThat(evaluator.hasPermission("ghost", Permission.AUDIT_READ)).isFalse();
    }

    @Test
    void hasPermission_returns_false_for_inactive_operator() {
        when(operators.findByOperatorId("op-2"))
                .thenReturn(Optional.of(operator(200L, "op-2", AdminOperator.Status.DISABLED.name())));

        assertThat(evaluator.hasPermission("op-2", Permission.AUDIT_READ)).isFalse();
    }

    @Test
    void hasAllPermissions_requires_full_containment() {
        when(operators.findByOperatorId("op-3"))
                .thenReturn(Optional.of(operator(300L, "op-3", AdminOperator.Status.ACTIVE.name())));
        when(operatorRoles.findByOperatorId(300L)).thenReturn(List.of(roleBinding(300L, 1L)));
        when(rolePermissions.findPermissionKeysByRoleIds(anyCollection()))
                .thenReturn(List.of(Permission.AUDIT_READ));

        assertThat(evaluator.hasAllPermissions("op-3",
                List.of(Permission.AUDIT_READ, Permission.SECURITY_EVENT_READ))).isFalse();
        assertThat(evaluator.hasAllPermissions("op-3", List.of(Permission.AUDIT_READ))).isTrue();
    }

    @Test
    void hasPermission_null_inputs_return_false() {
        lenient().when(operators.findByOperatorId("x")).thenReturn(Optional.empty());
        assertThat(evaluator.hasPermission(null, Permission.AUDIT_READ)).isFalse();
        assertThat(evaluator.hasPermission("x", null)).isFalse();
    }
}
