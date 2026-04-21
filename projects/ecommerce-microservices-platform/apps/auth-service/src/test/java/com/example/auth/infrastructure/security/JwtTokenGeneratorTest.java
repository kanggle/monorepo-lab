package com.example.auth.infrastructure.security;

import com.example.auth.domain.entity.User;
import com.example.auth.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenGenerator 단위 테스트")
class JwtTokenGeneratorTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes!!";
    private static final long TTL_SECONDS = 3600L;
    private static final String ISSUER = "auth-service";
    private static final String AUDIENCE = "api";

    private JwtTokenGenerator generator;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setAccessTokenTtlSeconds(TTL_SECONDS);
        props.setRefreshTokenTtlSeconds(2592000L);
        props.setIssuer(ISSUER);
        props.setAudience(AUDIENCE);
        props.initSecretKey();
        generator = new JwtTokenGenerator(props);
    }

    @Test
    @DisplayName("generateAccessToken - 유효한 JWT 문자열 반환")
    void generateAccessToken_returnsNonBlankToken() {
        User user = User.create("test@example.com", "hash", "홍길동");
        String token = generator.generateAccessToken(user);
        assertThat(token).isNotBlank().contains(".");
    }

    @Test
    @DisplayName("accessTokenTtlSeconds - 설정값 반환")
    void accessTokenTtlSeconds_returnsConfiguredValue() {
        assertThat(generator.accessTokenTtlSeconds()).isEqualTo(TTL_SECONDS);
    }

    @Test
    @DisplayName("생성자 - secret이 32바이트 미만이면 IllegalArgumentException 발생")
    void constructor_shortSecret_throws() {
        JwtProperties shortSecretProps = new JwtProperties();
        shortSecretProps.setSecret("short");
        shortSecretProps.setAccessTokenTtlSeconds(3600L);
        shortSecretProps.setRefreshTokenTtlSeconds(2592000L);
        shortSecretProps.setIssuer(ISSUER);
        shortSecretProps.setAudience(AUDIENCE);

        assertThatThrownBy(() -> new JwtTokenGenerator(shortSecretProps))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("생성자 - secret이 null이면 IllegalArgumentException 발생")
    void constructor_nullSecret_throws() {
        JwtProperties nullSecretProps = new JwtProperties();
        nullSecretProps.setSecret(null);
        nullSecretProps.setAccessTokenTtlSeconds(3600L);
        nullSecretProps.setRefreshTokenTtlSeconds(2592000L);
        nullSecretProps.setIssuer(ISSUER);
        nullSecretProps.setAudience(AUDIENCE);

        assertThatThrownBy(() -> new JwtTokenGenerator(nullSecretProps))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("생성자 - secret이 빈 문자열이면 IllegalArgumentException 발생")
    void constructor_blankSecret_throws() {
        JwtProperties blankSecretProps = new JwtProperties();
        blankSecretProps.setSecret("   ");
        blankSecretProps.setAccessTokenTtlSeconds(3600L);
        blankSecretProps.setRefreshTokenTtlSeconds(2592000L);
        blankSecretProps.setIssuer(ISSUER);
        blankSecretProps.setAudience(AUDIENCE);

        assertThatThrownBy(() -> new JwtTokenGenerator(blankSecretProps))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("empty");
    }
}
