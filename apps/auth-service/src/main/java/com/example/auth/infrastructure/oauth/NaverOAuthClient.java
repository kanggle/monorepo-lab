package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.service.OAuthProvider;
import com.example.auth.infrastructure.config.NaverOAuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Component
public class NaverOAuthClient implements OAuthProvider {

    private static final String PROVIDER = "naver";

    private static final String AUTH_ENDPOINT = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_ENDPOINT = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_ENDPOINT = "https://openapi.naver.com/v1/nid/me";

    private final NaverOAuthProperties props;
    private final RestClient restClient;

    public NaverOAuthClient(NaverOAuthProperties props) {
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
            .queryParam("state", state)
            .encode()
            .build()
            .toUriString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo fetchUserInfo(String code, String redirectUri) {
        // Step 1: code -> access_token
        // 네이버 Token API는 query parameter 방식만 지원
        String tokenUri = UriComponentsBuilder.fromUriString(TOKEN_ENDPOINT)
            .queryParam("grant_type", "authorization_code")
            .queryParam("client_id", props.getClientId())
            .queryParam("client_secret", props.getClientSecret())
            .queryParam("code", code)
            .build()
            .toUriString();
        Map<String, Object> tokenResponse = restClient.post()
            .uri(tokenUri)
            .retrieve()
            .body(Map.class);

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new IllegalStateException("Naver token response missing access_token");
        }
        String accessToken = (String) tokenResponse.get("access_token");

        // Step 2: access_token -> profile
        Map<String, Object> profileResponse = restClient.get()
            .uri(PROFILE_ENDPOINT)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(Map.class);

        if (profileResponse == null || !"00".equals(profileResponse.get("resultcode"))) {
            throw new IllegalStateException("Naver profile API returned error");
        }

        Map<String, Object> response = (Map<String, Object>) profileResponse.get("response");
        if (response == null) {
            throw new IllegalStateException("Naver profile response is null");
        }

        String email = (String) response.get("email");
        String name = (String) response.get("name");
        return new OAuthUserInfo(email, name);
    }
}
