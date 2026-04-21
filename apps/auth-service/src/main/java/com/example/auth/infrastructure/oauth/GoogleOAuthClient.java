package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.service.OAuthProvider;
import com.example.auth.infrastructure.config.GoogleOAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProvider {

    private static final String PROVIDER = "google";

    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo";
    private static final String SCOPE = "openid email profile";

    private final GoogleOAuthProperties props;
    private final RestClient restClient;

    public GoogleOAuthClient(GoogleOAuthProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String buildAuthorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(AUTH_ENDPOINT)
            .queryParam("client_id", props.getClientId())
            .queryParam("redirect_uri", redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", SCOPE)
            .queryParam("state", state)
            .queryParam("access_type", "online")
            .encode()
            .build()
            .toUriString();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
        // Step 1: code -> access_token
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restClient.post()
            .uri(TOKEN_ENDPOINT)
            .body(Map.of(
                "code", code,
                "client_id", props.getClientId(),
                "client_secret", props.getClientSecret(),
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code"
            ))
            .retrieve()
            .body(Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Google token response missing access_token");
        }
        String accessToken = (String) tokenResponse.get("access_token");

        // Step 2: access_token -> user info
        @SuppressWarnings("unchecked")
        Map<String, Object> userInfo = restClient.get()
            .uri(USERINFO_ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);

        if (userInfo == null) {
            throw new IllegalStateException("Google userinfo response is null");
        }

        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");
        return new OAuthUserInfo(email, name);
    }
}
