package com.wms.admin.application;

/**
 * Shared stateless utility helpers for admin-service application layer.
 *
 * <p>Visible to all sub-packages of {@code com.wms.admin.application}.
 * Call sites: {@link com.wms.admin.application.user.UserService},
 * {@link com.wms.admin.application.role.RoleService}.
 */
public final class DomainUtils {

    private DomainUtils() {
        // utility class — no instantiation
    }

    /**
     * Null-safe equality: returns {@code true} when both are {@code null} or
     * when {@code a.equals(b)}.
     */
    public static boolean equalsNullable(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
