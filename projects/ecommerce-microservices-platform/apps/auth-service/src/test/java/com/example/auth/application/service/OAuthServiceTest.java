package com.example.auth.application.service;

import com.example.auth.application.dto.OAuthCallbackResult;
import com.example.auth.application.dto.OAuthLoginCommand;
import com.example.auth.application.exception.OAuthException;
import com.example.auth.application.exception.OAuthUpstreamException;
import com.example.auth.domain.entity.Role;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.repository.UserSessionRegistry.RegistrationResult;
import com.example.auth.domain.service.OAuthCallbackProperties;
import com.example.auth.domain.service.OAuthProvider;
import com.example.auth.domain.service.OAuthProvider.OAuthUserInfo;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService 단위 테스트")
class OAuthServiceTest {

    private OAuthService oauthService;

    @Mock private OAuthProvider googleProvider;
    @Mock private OAuthProvider naverProvider;
    @Mock private OAuthStateStore oauthStateStore;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private TokenGenerator tokenGenerator;
    @Mock private TokenProperties tokenProperties;
    @Mock private SessionProperties sessionProperties;
    @Mock private UserSessionRegistry sessionRegistry;
    @Mock private OAuthCallbackProperties oauthCallbackProperties;
    @Mock private AuthEventPublisher authEventPublisher;

    @BeforeEach
    void setUp() {
        given(googleProvider.provider()).willReturn("google");
        given(naverProvider.provider()).willReturn("naver");
        oauthService = new OAuthService(
            List.of(googleProvider, naverProvider),
            oauthStateStore, userRepository, refreshTokenStore,
            tokenGenerator, tokenProperties, sessionProperties,
            sessionRegistry, oauthCallbackProperties, authEventPublisher
        );
    }

    @Test
    @DisplayName("buildAuthorizationUrl - 허용된 callbackUrl이면 state를 저장하고 인증 URL을 반환한다")
    void buildAuthorizationUrl_validCallbackUrl_savesStateAndReturnsUrl() {
        given(oauthCallbackProperties.allowedCallbackUrls())
            .willReturn(List.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("google"))
            .willReturn("http://localhost:8080/callback");
        given(googleProvider.buildAuthorizationUrl(anyString(), eq("http://localhost:8080/callback")))
            .willReturn("https://accounts.google.com/auth?state=abc");

        String url = oauthService.buildAuthorizationUrl("google", "http://localhost:3000/oauth/callback");

        assertThat(url).isEqualTo("https://accounts.google.com/auth?state=abc");
        then(oauthStateStore).should().save(anyString(), eq("http://localhost:3000/oauth/callback"), any());
    }

    @Test
    @DisplayName("buildAuthorizationUrl - 허용되지 않은 callbackUrl이면 OAuthException을 던진다")
    void buildAuthorizationUrl_invalidCallbackUrl_throwsOAuthException() {
        given(oauthCallbackProperties.allowedCallbackUrls())
            .willReturn(List.of("http://localhost:3000/oauth/callback"));

        assertThatThrownBy(() -> oauthService.buildAuthorizationUrl("google", "http://evil.com/callback"))
            .isInstanceOf(OAuthException.class)
            .hasMessageContaining("Invalid callbackUrl");
    }

    @Test
    @DisplayName("buildAuthorizationUrl - 지원하지 않는 provider면 OAuthException을 던진다")
    void buildAuthorizationUrl_unsupportedProvider_throwsOAuthException() {
        assertThatThrownBy(() -> oauthService.buildAuthorizationUrl("kakao", "http://localhost:3000/oauth/callback"))
            .isInstanceOf(OAuthException.class)
            .hasMessageContaining("Unsupported OAuth provider");
    }

    @Test
    @DisplayName("resolveCallbackUrl - 유효한 state이면 callbackUrl을 반환한다")
    void resolveCallbackUrl_validState_returnsCallbackUrl() {
        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));

        Optional<String> result = oauthService.resolveCallbackUrl("valid-state");

        assertThat(result).isPresent().hasValue("http://localhost:3000/oauth/callback");
    }

    @Test
    @DisplayName("resolveCallbackUrl - null state이면 empty를 반환한다")
    void resolveCallbackUrl_nullState_returnsEmpty() {
        assertThat(oauthService.resolveCallbackUrl(null)).isEmpty();
    }

    @Test
    @DisplayName("handleCallback - state 만료 시 OAuthException을 던진다")
    void handleCallback_expiredState_throwsOAuthException() {
        given(oauthStateStore.getAndDelete("expired-state")).willReturn(Optional.empty());

        OAuthLoginCommand command = new OAuthLoginCommand("code", "expired-state");

        assertThatThrownBy(() -> oauthService.handleCallback("google", command))
            .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("handleCallback - 기존 사용자면 신규 생성 없이 토큰을 발급한다")
    void handleCallback_existingUser_issuesTokens() {
        UUID userId = UUID.randomUUID();
        User existingUser = User.reconstitute(userId, "user@example.com", "hash", "홍길동",
            Role.CUSTOMER, null, Instant.now(), Instant.now(), true);

        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("google"))
            .willReturn("http://localhost:8080/callback");
        given(googleProvider.fetchUserInfo("code", "http://localhost:8080/callback"))
            .willReturn(new OAuthUserInfo("user@example.com", "홍길동"));
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(existingUser));
        given(tokenGenerator.generateAccessToken(existingUser)).willReturn("access-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);
        given(sessionRegistry.registerSession(eq(userId), anyString(), eq(604800L)))
            .willReturn(new RegistrationResult("new-hash", null));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");
        OAuthCallbackResult result = oauthService.handleCallback("google", command);

        assertThat(result.success()).isTrue();
        assertThat(result.callbackUrl()).isEqualTo("http://localhost:3000/oauth/callback");
        assertThat(result.loginResult().accessToken()).isEqualTo("access-token");
        then(userRepository).should().findByEmail("user@example.com");
        then(userRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("handleCallback - 신규 사용자면 CUSTOMER 역할로 생성 후 토큰을 발급한다")
    void handleCallback_newUser_createsAndIssuesTokens() {
        UUID newUserId = UUID.randomUUID();
        User newUser = User.reconstitute(newUserId, "new@naver.com", null, "새유저",
            Role.CUSTOMER, "naver", Instant.now(), Instant.now(), true);

        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("naver"))
            .willReturn("http://localhost:8081/callback");
        given(naverProvider.fetchUserInfo("code", "http://localhost:8081/callback"))
            .willReturn(new OAuthUserInfo("new@naver.com", "새유저"));
        given(userRepository.findByEmail("new@naver.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willReturn(newUser);
        given(tokenGenerator.generateAccessToken(newUser)).willReturn("access-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);
        given(sessionRegistry.registerSession(eq(newUserId), anyString(), eq(604800L)))
            .willReturn(new RegistrationResult("new-hash", null));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");
        OAuthCallbackResult result = oauthService.handleCallback("naver", command);

        assertThat(result.success()).isTrue();
        then(userRepository).should().save(any(User.class));
        then(authEventPublisher).should().publish(any());
    }

    @Test
    @DisplayName("handleCallback - 외부 API 오류 시 OAuthUpstreamException을 던진다")
    void handleCallback_apiError_throwsOAuthUpstreamException() {
        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("google"))
            .willReturn("http://localhost:8080/callback");
        given(googleProvider.fetchUserInfo(anyString(), anyString()))
            .willThrow(new org.springframework.web.client.RestClientException("API error"));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");

        assertThatThrownBy(() -> oauthService.handleCallback("google", command))
            .isInstanceOf(OAuthUpstreamException.class)
            .hasMessageContaining("google API call failed")
            .hasCauseInstanceOf(org.springframework.web.client.RestClientException.class);
    }

    @Test
    @DisplayName("handleCallback - 외부 API 오류 시 callbackUrl이 예외에 포함된다")
    void handleCallback_apiError_exceptionContainsCallbackUrl() {
        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("google"))
            .willReturn("http://localhost:8080/callback");
        given(googleProvider.fetchUserInfo(anyString(), anyString()))
            .willThrow(new org.springframework.web.client.RestClientException("API error"));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");

        assertThatThrownBy(() -> oauthService.handleCallback("google", command))
            .isInstanceOf(OAuthUpstreamException.class)
            .satisfies(ex -> {
                OAuthUpstreamException upstreamEx = (OAuthUpstreamException) ex;
                assertThat(upstreamEx.getCallbackUrl()).isEqualTo("http://localhost:3000/oauth/callback");
            });
    }

    @Test
    @DisplayName("handleCallback - 이메일이 없으면 failure 결과를 반환한다")
    void handleCallback_emailBlank_returnsFailure() {
        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("naver"))
            .willReturn("http://localhost:8081/callback");
        given(naverProvider.fetchUserInfo("code", "http://localhost:8081/callback"))
            .willReturn(new OAuthUserInfo("", "이름"));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");
        OAuthCallbackResult result = oauthService.handleCallback("naver", command);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("handleCallback - 비활성 사용자면 failure 결과를 반환한다")
    void handleCallback_deactivatedUser_returnsFailure() {
        UUID userId = UUID.randomUUID();
        User deactivatedUser = User.reconstitute(userId, "user@example.com", "hash", "홍길동",
            Role.CUSTOMER, null, Instant.now(), Instant.now(), false);

        given(oauthStateStore.getAndDelete("valid-state"))
            .willReturn(Optional.of("http://localhost:3000/oauth/callback"));
        given(oauthCallbackProperties.redirectUriFor("google"))
            .willReturn("http://localhost:8080/callback");
        given(googleProvider.fetchUserInfo("code", "http://localhost:8080/callback"))
            .willReturn(new OAuthUserInfo("user@example.com", "홍길동"));
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(deactivatedUser));

        OAuthLoginCommand command = new OAuthLoginCommand("code", "valid-state");
        OAuthCallbackResult result = oauthService.handleCallback("google", command);

        assertThat(result.success()).isFalse();
    }
}
