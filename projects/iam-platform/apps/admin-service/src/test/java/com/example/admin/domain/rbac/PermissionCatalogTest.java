package com.example.admin.domain.rbac;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-486 — pins {@link Permission#catalog()} (exposed by
 * {@code GET /api/admin/permissions}) to the reflected constant set so a new
 * permission key can never silently omit itself from the catalog, and the
 * {@code <missing>} audit sentinel can never leak into it.
 */
class PermissionCatalogTest {

    @Test
    void catalog_equals_every_real_permission_constant_excluding_missing_sentinel() throws Exception {
        Set<String> reflected = new LinkedHashSet<>();
        for (Field f : Permission.class.getDeclaredFields()) {
            if (!Modifier.isPublic(f.getModifiers())
                    || !Modifier.isStatic(f.getModifiers())
                    || f.getType() != String.class) {
                continue;
            }
            String value = (String) f.get(null);
            if (Permission.MISSING.equals(value)) {
                continue; // audit sentinel, not a grantable permission
            }
            reflected.add(value);
        }

        assertThat(Permission.catalog())
                .as("catalog() must expose exactly the real permission keys (no MISSING, no omissions)")
                .containsExactlyInAnyOrderElementsOf(reflected);
    }

    @Test
    void catalog_is_immutable_and_excludes_missing() {
        List<String> catalog = Permission.catalog();
        assertThat(catalog).doesNotContain(Permission.MISSING);
        assertThat(catalog).contains(
                Permission.ACCOUNT_READ, Permission.OPERATOR_MANAGE, Permission.PARTNERSHIP_MANAGE);
    }
}
