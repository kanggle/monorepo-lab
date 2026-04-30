package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Google OAuth 2.0 / OpenID Connect client.
 *
 * <p>Exchanges authorization code via POST to Google's token endpoint, then
 * cryptographically verifies the returned id_token (RS256 signature against
 * Google's published JWKS, {@code iss}/{@code aud}/{@code exp} claims) before
 * extracting user information. See {@link OidcJwksVerifier} (TASK-BE-145).
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final OAuthProperties.ProviderProperties props;
    private final ObjectMapper objectMapper;
    private final OidcJwksVerifier idTokenVerifier;

    public GoogleOAuthClient(OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        this(oAuthProperties, objectMapper, RestClient.builder().build());
    }

    GoogleOAuthClient(OAuthProperties oAuthProperties,
                      ObjectMapper objectMapper,
                      RestClient restClient) {
        this.props = oAuthProperties.getGoogle();
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.idTokenVerifier = new OidcJwksVerifier(
                props.getJwksUri(),
                props.getExpectedIssuerPattern(),
                props.getClientId(),
                restClient,
                objectMapper,
                props.getJwksCacheTtlMillis());
    }

    @Override
    public OAuthUserInfo exchangeCodeForUserInfo(String code, String redirectUri) {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("code", code);
            formData.add("redirect_uri", redirectUri);
            formData.add("client_id", props.getClientId());
            formData.add("client_secret", props.getClientSecret());

            String responseBody = restClient.post()
                    .uri(props.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);

            JsonNode tokenResponse = objectMapper.readTree(responseBody);

            String idToken = tokenResponse.path("id_token").asText(null);
            if (idToken == null || idToken.isBlank()) {
                throw new OAuthProviderException("Google token response missing id_token");
            }

            // TASK-BE-145: verify signature + iss + aud + exp before trusting claims.
            Map<String, Object> claims = idTokenVerifier.verify(idToken);

            String sub = stringClaim(claims, "sub");
            String email = stringClaim(claims, "email");
            String name = stringClaim(claims, "name");

            if (sub == null || sub.isBlank()) {
                throw new OAuthProviderException("Google id_token missing 'sub' claim");
            }

            return new OAuthUserInfo(sub, email, name, OAuthProvider.GOOGLE);

        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth code exchange failed: {}", e.getMessage());
            throw new OAuthProviderException("Google OAuth provider error", e);
        }
    }

    private static String stringClaim(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : v.toString();
    }
}
