package com.example.admin.infrastructure.security;

import com.example.admin.application.OperatorContext;
import com.example.admin.application.exception.OperatorUnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OperatorContextHolder 단위 테스트")
class OperatorContextHolderUnitTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("유효한 OperatorContext principal → require() 가 컨텍스트 반환")
    void require_validContext_returnsOperatorContext() {
        OperatorContext ctx = new OperatorContext("op-1", "jti-abc");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ctx, null));

        assertThat(OperatorContextHolder.require()).isEqualTo(ctx);
    }

    @Test
    @DisplayName("Authentication 없음 → OperatorUnauthorizedException")
    void require_noAuthentication_throwsOperatorUnauthorizedException() {
        assertThatThrownBy(OperatorContextHolder::require)
                .isInstanceOf(OperatorUnauthorizedException.class);
    }

    @Test
    @DisplayName("principal 타입이 OperatorContext가 아님 → OperatorUnauthorizedException")
    void require_wrongPrincipalType_throwsOperatorUnauthorizedException() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-an-operator", null));

        assertThatThrownBy(OperatorContextHolder::require)
                .isInstanceOf(OperatorUnauthorizedException.class);
    }
}
