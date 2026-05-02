package com.example.admin.application;

import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminRoleJpaRepository;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;

/**
 * Shared test helpers for the operator administration use case unit tests
 * ({@code CreateOperatorUseCaseTest}, {@code PatchOperatorRoleUseCaseTest}).
 *
 * <p>Extracted as part of TASK-BE-125 to remove verbatim duplication of the
 * reflective constructors and field setters that both tests previously carried.
 * Behaviour must remain identical to the original inlined helpers — this class
 * exists for de-duplication only, not to introduce new fixtures.
 *
 * <p>Package-private by design — only collaborators inside
 * {@code com.example.admin.application} test package may use it.
 */
final class OperatorUseCaseTestSupport {

    private OperatorUseCaseTestSupport() {
        // utility — no instances
    }

    /** Default actor used by both operator-admin use case tests. */
    static OperatorContext actor() {
        return new OperatorContext("actor-uuid", "jti-1");
    }

    /**
     * Reflectively instantiate the package-private {@link OperatorRoleResolver}
     * from this test package. Mirrors the original helpers in both test classes.
     */
    static OperatorRoleResolver newResolver(
            AdminOperatorJpaRepository operatorRepository,
            AdminRoleJpaRepository roleRepository) {
        try {
            Constructor<OperatorRoleResolver> ctor =
                    OperatorRoleResolver.class.getDeclaredConstructor(
                            AdminOperatorJpaRepository.class, AdminRoleJpaRepository.class);
            ctor.setAccessible(true);
            return ctor.newInstance(operatorRepository, roleRepository);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Build an {@link AdminRoleJpaEntity} fixture with the given id/name. Uses
     * the no-arg constructor + reflective field assignment because the JPA entity
     * does not expose a public constructor for ad-hoc fixtures.
     */
    static AdminRoleJpaEntity role(Long id, String name) {
        try {
            Constructor<AdminRoleJpaEntity> ctor = AdminRoleJpaEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            AdminRoleJpaEntity r = ctor.newInstance();
            setField(r, "id", id);
            setField(r, "name", name);
            setField(r, "description", name);
            return r;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Build an {@link AdminOperatorJpaEntity} fixture with the given id/uuid/email/status.
     * Uses {@link AdminOperatorJpaEntity#create} for canonical defaults and reflectively
     * assigns the JPA-internal id afterwards.
     */
    static AdminOperatorJpaEntity operator(Long id, String uuid, String email, String status) {
        // TASK-BE-249: use 7-arg factory that includes tenantId
        AdminOperatorJpaEntity e = AdminOperatorJpaEntity.create(
                uuid, email, "hash", "Display", status, "fan-platform",
                Instant.parse("2026-01-01T00:00:00Z"));
        setField(e, "id", id);
        return e;
    }

    /** Reflectively set a field (including inherited) on the given target. */
    static void setField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Walk the class hierarchy looking for a declared field with the given name. */
    static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
