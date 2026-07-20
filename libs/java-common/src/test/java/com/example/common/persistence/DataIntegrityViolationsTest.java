package com.example.common.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataIntegrityViolations.isUniqueViolation")
class DataIntegrityViolationsTest {

    @Test
    @DisplayName("SQLSTATE 23505 nested several layers deep is detected")
    void detectsUniqueViolationDeepInCauseChain() {
        SQLException unique = new SQLException("duplicate key", "23505");
        Throwable wrapped = new RuntimeException("spring wrapper",
                new IllegalStateException("hibernate layer", unique));

        assertThat(DataIntegrityViolations.isUniqueViolation(wrapped)).isTrue();
    }

    @Test
    @DisplayName("a non-unique SQLSTATE (23503 = FK violation) is NOT reported as unique")
    void foreignKeyViolationIsNotUnique() {
        SQLException fk = new SQLException("FK violation", "23503");
        assertThat(DataIntegrityViolations.isUniqueViolation(new RuntimeException(fk))).isFalse();
    }

    @Test
    @DisplayName("a NOT NULL SQLSTATE (23502) is NOT reported as unique")
    void notNullViolationIsNotUnique() {
        SQLException notNull = new SQLException("not null", "23502");
        assertThat(DataIntegrityViolations.isUniqueViolation(new RuntimeException(notNull))).isFalse();
    }

    @Test
    @DisplayName("MySQL duplicate-key (vendor code 1062, SQLSTATE 23000) is detected")
    void detectsMysqlDuplicateEntry() {
        // MySQL folds every integrity violation under SQLSTATE 23000 and distinguishes by vendor code.
        SQLException dup = new SQLException("Duplicate entry", "23000", 1062);
        assertThat(DataIntegrityViolations.isUniqueViolation(new RuntimeException(dup))).isTrue();
    }

    @Test
    @DisplayName("MySQL FK violation (vendor code 1452, SQLSTATE 23000) is NOT unique")
    void mysqlForeignKeyIsNotUnique() {
        SQLException fk = new SQLException("Cannot add or update a child row", "23000", 1452);
        assertThat(DataIntegrityViolations.isUniqueViolation(new RuntimeException(fk))).isFalse();
    }

    @Test
    @DisplayName("MySQL NOT NULL violation (vendor code 1048, SQLSTATE 23000) is NOT unique")
    void mysqlNotNullIsNotUnique() {
        SQLException notNull = new SQLException("Column cannot be null", "23000", 1048);
        assertThat(DataIntegrityViolations.isUniqueViolation(new RuntimeException(notNull))).isFalse();
    }

    @Test
    @DisplayName("a chain with no SQLException is not unique")
    void noSqlExceptionInChain() {
        assertThat(DataIntegrityViolations.isUniqueViolation(
                new RuntimeException("boom", new IllegalArgumentException("no sql here")))).isFalse();
    }

    @Test
    @DisplayName("null is false, not an NPE")
    void nullIsFalse() {
        assertThat(DataIntegrityViolations.isUniqueViolation(null)).isFalse();
    }

    @Test
    @DisplayName("a self-referential cause cycle terminates and returns false")
    void selfReferentialCauseDoesNotLoop() {
        SQLException notUnique = new SQLException("weird", "08000");
        // A throwable whose cause is itself would loop a naive walker; the guard stops it.
        assertThat(DataIntegrityViolations.isUniqueViolation(notUnique)).isFalse();
    }
}
