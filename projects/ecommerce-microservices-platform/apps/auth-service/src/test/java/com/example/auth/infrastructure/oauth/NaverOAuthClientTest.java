package com.example.auth.infrastructure.oauth;

import com.example.auth.infrastructure.config.NaverOAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("NaverOAuthClient 단위 테스트")
class NaverOAuthClientTest {

    @Mock
    private NaverOAuthProperties props;

    @Test
    @DisplayName("provider()는 'naver' 문자열을 반환한다")
    void provider_returnsCorrectName() {
        NaverOAuthClient sut = new NaverOAuthClient(props);

        String result = sut.provider();

        assertThat(result).isEqualTo("naver");
    }

    @Test
    @DisplayName("buildAuthorizationUrl()은 client_id, redirect_uri, response_type, state 파라미터를 포함한 URL을 반환한다")
    void buildAuthorizationUrl_includesRequiredParams() {
        given(props.getClientId()).willReturn("test-naver-client-id");
        NaverOAuthClient sut = new NaverOAuthClient(props);
        String state = "test-state-value";
        String redirectUri = "https://example.com/callback";

        String url = sut.buildAuthorizationUrl(state, redirectUri);

        assertThat(url)
            .contains("client_id=test-naver-client-id")
            .contains("response_type=code")
            .contains("state=test-state-value")
            .contains("redirect_uri=");
    }

    @Test
    @DisplayName("buildAuthorizationUrl()은 전달된 state 값을 URL에 포함한다")
    void buildAuthorizationUrl_includesState() {
        given(props.getClientId()).willReturn("test-naver-client-id");
        NaverOAuthClient sut = new NaverOAuthClient(props);
        String state = "unique-state-xyz789";
        String redirectUri = "https://example.com/callback";

        String url = sut.buildAuthorizationUrl(state, redirectUri);

        assertThat(url).contains("state=unique-state-xyz789");
    }
}
