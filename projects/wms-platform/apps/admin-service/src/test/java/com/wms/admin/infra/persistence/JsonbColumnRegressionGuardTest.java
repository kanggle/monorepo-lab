package com.wms.admin.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * JSONB regression guard — every entity field annotated with
 * {@code columnDefinition = "jsonb"} MUST also carry
 * {@code @JdbcTypeCode(SqlTypes.JSON)}, otherwise Hibernate 6.x defaults to
 * OID parameter binding and writes garbage. TASK-SCM-INT-001b root cause #2 +
 * TASK-SCM-BE-005 + TASK-BE-043 regression-guard learning, ported to
 * admin-service (AC-05).
 */
class JsonbColumnRegressionGuardTest {

    static Stream<Class<?>> entitiesWithJsonb() {
        return Stream.of(
                AdminRoleJpaEntity.class,
                AdminSettingJpaEntity.class,
                AdminOutboxJpaEntity.class);
    }

    @ParameterizedTest
    @MethodSource("entitiesWithJsonb")
    void everyJsonbColumnHasJdbcTypeCode(Class<?> entityClass) {
        List<Field> jsonbFields = jsonbFields(entityClass);
        assertThat(jsonbFields)
                .as("expected at least one jsonb column on " + entityClass.getSimpleName())
                .isNotEmpty();
        for (Field f : jsonbFields) {
            JdbcTypeCode code = f.getAnnotation(JdbcTypeCode.class);
            assertThat(code)
                    .as("jsonb column %s.%s must carry @JdbcTypeCode(SqlTypes.JSON) "
                            + "(TASK-SCM-INT-001b root cause #2)",
                            entityClass.getSimpleName(), f.getName())
                    .isNotNull();
            assertThat(code.value()).isEqualTo(SqlTypes.JSON);
        }
    }

    private static List<Field> jsonbFields(Class<?> entityClass) {
        return java.util.Arrays.stream(entityClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(Column.class))
                .filter(f -> "jsonb".equalsIgnoreCase(f.getAnnotation(Column.class).columnDefinition()))
                .toList();
    }
}
