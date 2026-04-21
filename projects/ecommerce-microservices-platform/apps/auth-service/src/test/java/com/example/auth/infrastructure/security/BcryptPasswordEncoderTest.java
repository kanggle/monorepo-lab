package com.example.auth.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BcryptPasswordEncoder 단위 테스트")
class BcryptPasswordEncoderTest {

    @InjectMocks
    private BcryptPasswordEncoder bcryptPasswordEncoder;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder delegate;

    @Test
    @DisplayName("encode 호출 시 Spring PasswordEncoder에 위임하고 결과를 반환한다")
    void encode_rawPassword_delegatesToSpringEncoderAndReturnsResult() {
        String rawPassword = "password1!";
        String encodedPassword = "$2a$10$hashedValue";
        given(delegate.encode(rawPassword)).willReturn(encodedPassword);

        String result = bcryptPasswordEncoder.encode(rawPassword);

        assertThat(result).isEqualTo(encodedPassword);
    }

    @Test
    @DisplayName("delegate가 true를 반환하면 matches는 true를 반환한다")
    void matches_delegateReturnsTrue_returnsTrue() {
        String rawPassword = "password1!";
        String encodedPassword = "$2a$10$hashedValue";
        given(delegate.matches(rawPassword, encodedPassword)).willReturn(true);

        boolean result = bcryptPasswordEncoder.matches(rawPassword, encodedPassword);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("delegate가 false를 반환하면 matches는 false를 반환한다")
    void matches_delegateReturnsFalse_returnsFalse() {
        String rawPassword = "wrongPassword";
        String encodedPassword = "$2a$10$hashedValue";
        given(delegate.matches(rawPassword, encodedPassword)).willReturn(false);

        boolean result = bcryptPasswordEncoder.matches(rawPassword, encodedPassword);

        assertThat(result).isFalse();
    }
}
