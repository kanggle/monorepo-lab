package com.example.auth.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyPathValidatorTest {

    @Test
    @DisplayName("prod 프로파일 + classpath private key 경로 → 부팅 실패")
    void validate_nonDevProfile_classpathPrivateKey_throws() {
        MockEnvironment env = new MockEnvironment().withProperty("foo", "bar");
        env.setActiveProfiles("prod");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "file:/var/secrets/jwt/public.pem");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY_PATH")
                .hasMessageContaining("classpath:keys/private.pem");
    }

    @Test
    @DisplayName("prod 프로파일 + classpath public key 경로 → 부팅 실패")
    void validate_nonDevProfile_classpathPublicKey_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "file:/var/secrets/jwt/private.pem", "classpath:keys/public.pem");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PUBLIC_KEY_PATH")
                .hasMessageContaining("classpath:keys/public.pem");
    }

    @Test
    @DisplayName("prod 프로파일 + 두 경로 모두 filesystem → 정상 통과")
    void validate_nonDevProfile_filesystemPaths_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "file:/var/secrets/jwt/private.pem", "file:/var/secrets/jwt/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("staging 프로파일도 비-development 으로 간주되어 classpath 거부")
    void validate_stagingProfile_classpath_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("local 프로파일 + classpath 경로 → 통과 (개발 편의)")
    void validate_localProfile_classpath_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("test 프로파일 + classpath 경로 → 통과 (테스트 픽스처 사용)")
    void validate_testProfile_classpath_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("integration-test 프로파일 + classpath 경로 → 통과")
    void validate_integrationTestProfile_classpath_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("integration-test");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("활성 프로파일 미지정 + classpath 경로 → 부팅 실패")
    void validate_noActiveProfile_classpath_throws() {
        MockEnvironment env = new MockEnvironment();

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("local + 다른 프로파일 동시 활성 → 통과 (개발 환경 우선)")
    void validate_multipleProfilesIncludingLocal_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local", "feature-x");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("e2e 프로파일 + classpath 경로 → 통과 (CI docker-compose 환경)")
    void validate_e2eProfile_classpath_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("e2e");

        JwtKeyPathValidator validator = new JwtKeyPathValidator(
                env, "classpath:keys/private.pem", "classpath:keys/public.pem");

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ALLOWED_CLASSPATH_PROFILES 상수가 local/test/integration-test/e2e 만 포함")
    void allowedClasspathProfiles_isExpectedSet() {
        assertThat(JwtKeyPathValidator.ALLOWED_CLASSPATH_PROFILES)
                .containsExactlyInAnyOrder("local", "test", "integration-test", "e2e");
    }
}
