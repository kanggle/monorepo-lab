package com.example.web.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AccessDeniedException 단위 테스트")
class AccessDeniedExceptionTest {

    @Test
    @DisplayName("기본 생성자 사용 시 기본 메시지를 가진다")
    void defaultConstructor_setsDefaultMessage() {
        AccessDeniedException ex = new AccessDeniedException();
        assertThat(ex.getMessage()).isEqualTo("Insufficient permissions to access resource");
    }

    @Test
    @DisplayName("메시지 생성자 사용 시 지정된 메시지를 가진다")
    void messageConstructor_setsGivenMessage() {
        AccessDeniedException ex = new AccessDeniedException("Admin role required");
        assertThat(ex.getMessage()).isEqualTo("Admin role required");
    }

    @Test
    @DisplayName("RuntimeException을 상속한다")
    void isRuntimeException() {
        AccessDeniedException ex = new AccessDeniedException();
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
