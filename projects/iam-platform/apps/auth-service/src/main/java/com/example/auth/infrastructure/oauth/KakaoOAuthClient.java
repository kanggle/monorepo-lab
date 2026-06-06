package com.example.auth.infrastructure.oauth;

import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Kakao OAuth 2.0 client.
 * Exchanges authorization code via POST to Kakao's token endpoint,
 * then calls the user info endpoint to get profile data.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final OAuthProperties.ProviderProperties props;
    private final ObjectMapper objectMapper;

    public KakaoOAuthClient(OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        this.props = oAuthProperties.getKakao();
        this.objectMapper = objectMapper;
        // Force HTTP/1.1 to avoid JDK HttpClient HTTP/2 RST_STREAM race against
        // WireMock stubs on Linux epoll event loops. (TASK-BE-273 Phase 2, ADR-004)
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(jdkClient))
                .build();
    }

    @Override
    public OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
        try {
            // Step 1: Exchange code for access token
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("code", code);
            formData.add("redirect_uri", redirectUri);
            formData.add("client_id", props.getClientId());
            formData.add("client_secret", props.getClientSecret());

            String tokenResponseBody = restClient.post()
                    .uri(props.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody);
            String accessToken = tokenResponse.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new OAuthProviderException("Kakao token response missing access_token");
            }

            // Step 2: Get user info
            String userInfoBody = restClient.get()
                    .uri(props.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode userInfo = objectMapper.readTree(userInfoBody);

            String id = String.valueOf(userInfo.path("id").asLong());
            JsonNode kakaoAccount = userInfo.path("kakao_account");
            String email = kakaoAccount.path("email").asText(null);
            JsonNode profile = kakaoAccount.path("profile");
            String nickname = profile.path("nickname").asText(null);

            return new OAuthUserInfo(id, email, nickname, OAuthProvider.KAKAO);

        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao OAuth code exchange failed: {}", e.getMessage());
            throw new OAuthProviderException("Kakao OAuth provider error", e);
        }
    }
}
