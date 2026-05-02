package com.example.account.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountRoleName 정규식 검증 — TASK-BE-255")
class AccountRoleNameTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN",
            "MEMBER",
            "WAREHOUSE_ADMIN",
            "INBOUND_OPERATOR",
            "ROLE_2",
            "X"
    })
    @DisplayName("규격 준수 — 대문자 시작, 대문자/숫자/_ 만 허용")
    void validRoleName_passes(String value) {
        assertThatCode(() -> AccountRoleName.validate(value))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "admin",            // lowercase first
            "MEMBER ",          // trailing space
            "Warehouse_Admin",  // mixed case
            "_ADMIN",           // underscore first
            "9ADMIN",           // digit first
            "AD-MIN",           // hyphen
            "AD.MIN",           // dot
            "AD MIN",           // space inside
            ""                  // blank handled separately, also fails regex
    })
    @DisplayName("규격 위반 — IllegalArgumentException")
    void invalidRoleName_throws(String value) {
        assertThatThrownBy(() -> AccountRoleName.validate(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 입력 → IllegalArgumentException")
    void nullRoleName_throws() {
        assertThatThrownBy(() -> AccountRoleName.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("최대 길이 64자 초과 → IllegalArgumentException")
    void tooLongRoleName_throws() {
        String tooLong = "A".repeat(65);
        assertThatThrownBy(() -> AccountRoleName.validate(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("64");
    }

    @Test
    @DisplayName("최대 길이 64자 정확히는 허용")
    void exactlyMaxLength_passes() {
        String exact = "A".repeat(64);
        assertThatCode(() -> AccountRoleName.validate(exact))
                .doesNotThrowAnyException();
    }
}
