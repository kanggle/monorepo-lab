package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Microsoft Identity Platform (Azure AD v2.0) OAuth 2.0 / OpenID Connect client.
 *
 * <p>Exchanges authorization code via POST to the Microsoft token endpoint,
 * then cryptographically verifies the id_token (RS256 against Microsoft's
 * tenant-aware JWKS, {@code iss} regex match for multi-tenant flows,
 * {@code aud} == client_id, {@code exp}) before extracting user information.
 *
 * <p>Email fallback: Microsoft returns {@code email} only when the user has
 * verified email on their account. When absent, {@code preferred_username} is
 * used as a fallback. If both are missing, the resulting {@link OAuthUserInfo}
 * carries {@code null} email and the use-case layer maps it to EMAIL_REQUIRED.
 */
@Slf4j
@Component
public class MicrosoftOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final OAuthProperties.ProviderProperties props;
    private final ObjectMapper objectMapper;
    private final OidcJwksVerifier idTokenVerifier;

    /**
     * Production constructor — Spring uses this one. The {@code @Autowired}
     * annotation is required because this class declares a second
     * (package-private) constructor for testability; with multiple constructors
     * Spring 6.x cannot auto-select one and falls back to looking for a no-arg
     * constructor, which fails ({@code "No default constructor found"}).
     * See TASK-BE-237.
     */
    @Autowired
    public MicrosoftOAuthClient(OAuthProperties oAuthProperties, ObjectMapper objectMapper) {
        this(oAuthProperties, objectMapper, RestClient.builder().build());
    }

    MicrosoftOAuthClient(OAuthProperties oAuthProperties,
                         ObjectMapper objectMapper,
                         RestClient restClient) {
        this.props = oAuthProperties.getMicrosoft();
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
                throw new OAuthProviderException("Microsoft token response missing id_token");
            }

            // TASK-BE-145: cryptographic verification before trusting claims.
            Map<String, Object> claims = idTokenVerifier.verify(idToken);

            String sub = stringClaim(claims, "sub");
            if (sub == null || sub.isBlank()) {
                throw new OAuthProviderException("Microsoft id_token missing 'sub' claim");
            }

            String email = stringClaim(claims, "email");
            if (email == null || email.isBlank()) {
                String preferredUsername = stringClaim(claims, "preferred_username");
                if (preferredUsername != null && !preferredUsername.isBlank()
                        && preferredUsername.contains("@")) {
                    email = preferredUsername;
                } else {
                    email = null;
                }
            }

            String name = stringClaim(claims, "name");

            return new OAuthUserInfo(sub, email, name, OAuthProvider.MICROSOFT);

        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Microsoft OAuth code exchange failed: {}", e.getMessage());
            throw new OAuthProviderException("Microsoft OAuth provider error", e);
        }
    }

    private static String stringClaim(Map<String, Object> claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : v.toString();
    }
}
