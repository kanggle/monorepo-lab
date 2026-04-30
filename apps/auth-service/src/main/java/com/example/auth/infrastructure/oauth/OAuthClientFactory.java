package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Returns the correct OAuthClient implementation for a given provider.
 */
@Component
@RequiredArgsConstructor
public class OAuthClientFactory {

    private final GoogleOAuthClient googleOAuthClient;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final MicrosoftOAuthClient microsoftOAuthClient;

    public OAuthClient getClient(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> googleOAuthClient;
            case KAKAO -> kakaoOAuthClient;
            case MICROSOFT -> microsoftOAuthClient;
        };
    }
}
