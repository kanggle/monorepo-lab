package com.example.membership.support;

import java.lang.reflect.Field;

/**
 * Test-only reflection helper for seeding private fields on domain objects under test.
 *
 * <p>Consolidates the {@code setField} helper that was copy-pasted across the
 * subscription use-case, recorder, repository, and scheduler tests. Reflective failures
 * (a wrong field name) are wrapped in an unchecked {@link IllegalStateException} so
 * callers need not declare a checked exception.
 */
public final class ReflectionTestSupport {

    private ReflectionTestSupport() {
    }

    public static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
