package com.example.auth.infrastructure.oauth;

import com.example.auth.infrastructure.config.GoogleOAuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoogleOAuthClient 단위 테스트")
class GoogleOAuthClientTest {

    @Mock
    private GoogleOAuthProperties props;

    @Test
    @DisplayName("provider()는 'google' 문자열을 반환한다")
    void provider_returnsCorrectName() {
        GoogleOAuthClient sut = new GoogleOAuthClient(props);

        String result = sut.provider();

        assertThat(result).isEqualTo("google");
    }

    @Test
    @DisplayName("buildAuthorizationUrl()은 client_id, redirect_uri, response_type, state 파라미터를 포함한 URL을 반환한다")
    void buildAuthorizationUrl_includesRequiredParams() {
        given(props.getClientId()).willReturn("test-google-client-id");
        GoogleOAuthClient sut = new GoogleOAuthClient(props);
        String state = "test-state-value";
        String redirectUri = "https://example.com/callback";

        String url = sut.buildAuthorizationUrl(state, redirectUri);

        assertThat(url)
            .contains("client_id=test-google-client-id")
            .contains("response_type=code")
            .contains("state=test-state-value")
            .contains("redirect_uri=");
    }

    @Test
    @DisplayName("buildAuthorizationUrl()은 전달된 state 값을 URL에 포함한다")
    void buildAuthorizationUrl_includesState() {
        given(props.getClientId()).willReturn("test-google-client-id");
        GoogleOAuthClient sut = new GoogleOAuthClient(props);
        String state = "unique-state-abc123";
        String redirectUri = "https://example.com/callback";

        String url = sut.buildAuthorizationUrl(state, redirectUri);

        assertThat(url).contains("state=unique-state-abc123");
    }
}
