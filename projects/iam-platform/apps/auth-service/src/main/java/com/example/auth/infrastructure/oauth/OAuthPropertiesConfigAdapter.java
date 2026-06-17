package com.example.auth.infrastructure.oauth;

import com.example.auth.application.port.OAuthProviderConfig;
import com.example.auth.application.port.OAuthProviderConfigPort;
import com.example.auth.domain.oauth.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapts the Spring {@code @ConfigurationProperties} {@link OAuthProperties} to
 * the application-layer {@link OAuthProviderConfigPort}.
 *
 * <p>Pure field mapping: the resolved {@code allowedRedirectUris} reproduces
 * {@link OAuthProperties.ProviderProperties#resolveAllowedRedirectUris()}
 * verbatim (non-empty allowlist → defensive copy; else single configured
 * {@code redirectUri}; else empty) so the redirect-URI exact-match validation
 * stays byte-identical.
 */
@Component
@RequiredArgsConstructor
public class OAuthPropertiesConfigAdapter implements OAuthProviderConfigPort {

    private final OAuthProperties oAuthProperties;

    @Override
    public OAuthProviderConfig get(OAuthProvider provider) {
        OAuthProperties.ProviderProperties props = switch (provider) {
            case GOOGLE -> oAuthProperties.getGoogle();
            case KAKAO -> oAuthProperties.getKakao();
            case MICROSOFT -> oAuthProperties.getMicrosoft();
            case NAVER -> oAuthProperties.getNaver();
        };
        return new OAuthProviderConfig(
                props.getClientId(),
                props.getAuthUri(),
                props.getScopes(),
                props.getRedirectUri(),
                props.resolveAllowedRedirectUris());
    }
}
