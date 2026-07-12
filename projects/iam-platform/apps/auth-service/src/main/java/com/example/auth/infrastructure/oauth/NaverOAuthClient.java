package com.example.auth.infrastructure.oauth;

import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.port.OAuthClient;
import com.example.auth.domain.oauth.OAuthProvider;
import com.example.auth.domain.oauth.OAuthUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static com.example.auth.infrastructure.oauth.OAuthClientSupport.buildHttp11RestClient;

/**
 * Naver OAuth 2.0 client (TASK-BE-397, ADR-006).
 *
 * <p>Mirrors {@link KakaoOAuthClient}: a non-OIDC OAuth2 provider (no {@code id_token},
 * so no JWKS signature verification). Exchanges the authorization code via POST to
 * Naver's token endpoint, then calls the user-info endpoint to read the profile.
 *
 * <p>Naver wraps the profile in a {@code response} object:
 * {@code { "resultcode": "00", "message": "success",
 *          "response": { "id": "...", "email": "...", "name": "...", "nickname": "..." } }}.
 * {@code provider_user_id} = {@code response.id} (stable per app-user).
 */
@Slf4j
@Component
public class NaverOAuthClient implements OAuthClient {

    private static final String SUCCESS_RESULT_CODE = "00";

    private final RestClient restClient;
    private final OAuthProperties.ProviderProperties props;
    private final ObjectMapper objectMapper;

    public NaverOAuthClient(OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        this.props = oAuthProperties.getNaver();
        this.objectMapper = objectMapper;
        this.restClient = buildHttp11RestClient();
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

            // TASK-MONO-350: a 4xx on the TOKEN call is the provider rejecting the
            // authorization code (invalid_grant) → 401 INVALID_CODE. The user-info call
            // below is deliberately NOT wrapped: a 4xx there is a bad/insufficient access
            // token, which is a different fault and must stay 502 PROVIDER_ERROR.
            String tokenResponseBody;
            try {
                tokenResponseBody = restClient.post()
                        .uri(props.getTokenUri())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formData)
                        .retrieve()
                        .body(String.class);
            } catch (RestClientException e) {
                throw OAuthClientSupport.classifyTokenExchangeFailure("Naver", e);
            }

            JsonNode tokenResponse = objectMapper.readTree(tokenResponseBody);
            String accessToken = tokenResponse.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new OAuthProviderException("Naver token response missing access_token");
            }

            // Step 2: Get user info
            String userInfoBody = restClient.get()
                    .uri(props.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode userInfo = objectMapper.readTree(userInfoBody);

            String resultCode = userInfo.path("resultcode").asText(null);
            if (resultCode != null && !SUCCESS_RESULT_CODE.equals(resultCode)) {
                throw new OAuthProviderException(
                        "Naver user-info returned resultcode=" + resultCode);
            }

            JsonNode response = userInfo.path("response");
            String id = response.path("id").asText(null);
            if (id == null || id.isBlank()) {
                throw new OAuthProviderException("Naver user-info missing response.id");
            }
            String email = response.path("email").asText(null);
            String name = response.path("name").asText(null);

            return new OAuthUserInfo(id, email, name, OAuthProvider.NAVER);

        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Naver OAuth code exchange failed: {}", e.getMessage());
            throw new OAuthProviderException("Naver OAuth provider error", e);
        }
    }
}
