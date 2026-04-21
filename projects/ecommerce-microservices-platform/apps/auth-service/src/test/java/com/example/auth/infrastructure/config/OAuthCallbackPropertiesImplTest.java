package com.example.auth.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthCallbackPropertiesImpl 단위 테스트")
class OAuthCallbackPropertiesImplTest {

    @Mock private OAuthCommonProperties commonProps;
    @Mock private GoogleOAuthProperties googleProps;
    @Mock private NaverOAuthProperties naverProps;

    private OAuthCallbackPropertiesImpl sut() {
        given(googleProps.getRedirectUri()).willReturn("https://example.com/callback/google");
        given(naverProps.getRedirectUri()).willReturn("https://example.com/callback/naver");
        return new OAuthCallbackPropertiesImpl(commonProps, googleProps, naverProps);
    }

    @Test
    @DisplayName("callbackAllowlist에 유효한 URL 목록이 있으면 모든 URL을 반환한다")
    void allowedCallbackUrls_withValidList_returnsAllUrls() {
        given(commonProps.getCallbackAllowlist())
            .willReturn("https://app.example.com, https://admin.example.com, https://store.example.com");

        List<String> result = sut().allowedCallbackUrls();

        assertThat(result).containsExactly(
            "https://app.example.com",
            "https://admin.example.com",
            "https://store.example.com"
        );
    }

    @Test
    @DisplayName("callbackAllowlist가 빈 문자열이면 빈 리스트를 반환한다")
    void allowedCallbackUrls_withBlankAllowlist_returnsEmptyList() {
        given(commonProps.getCallbackAllowlist()).willReturn("   ");

        List<String> result = sut().allowedCallbackUrls();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("callbackAllowlist가 null이면 빈 리스트를 반환한다")
    void allowedCallbackUrls_withNullAllowlist_returnsEmptyList() {
        given(commonProps.getCallbackAllowlist()).willReturn(null);

        List<String> result = sut().allowedCallbackUrls();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("provider가 google이면 Google redirectUri를 반환한다")
    void redirectUriFor_google_returnsGoogleUri() {
        String result = sut().redirectUriFor("google");

        assertThat(result).isEqualTo("https://example.com/callback/google");
    }

    @Test
    @DisplayName("provider가 naver이면 Naver redirectUri를 반환한다")
    void redirectUriFor_naver_returnsNaverUri() {
        String result = sut().redirectUriFor("naver");

        assertThat(result).isEqualTo("https://example.com/callback/naver");
    }

    @Test
    @DisplayName("알 수 없는 provider를 요청하면 IllegalArgumentException을 던진다")
    void redirectUriFor_unknownProvider_throwsException() {
        assertThatThrownBy(() -> sut().redirectUriFor("kakao"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kakao");
    }
}
