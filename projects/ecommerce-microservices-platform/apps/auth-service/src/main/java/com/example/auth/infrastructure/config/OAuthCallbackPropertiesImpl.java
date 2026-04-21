package com.example.auth.infrastructure.config;

import com.example.auth.domain.service.OAuthCallbackProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class OAuthCallbackPropertiesImpl implements OAuthCallbackProperties {

    private final OAuthCommonProperties commonProps;
    private final Map<String, String> redirectUris;

    public OAuthCallbackPropertiesImpl(OAuthCommonProperties commonProps,
                                        GoogleOAuthProperties googleProps,
                                        NaverOAuthProperties naverProps) {
        this.commonProps = commonProps;
        this.redirectUris = Map.of(
            "google", googleProps.getRedirectUri(),
            "naver", naverProps.getRedirectUri()
        );
    }

    @Override
    public List<String> allowedCallbackUrls() {
        String allowlist = commonProps.getCallbackAllowlist();
        if (allowlist == null || allowlist.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowlist.split(","))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }

    @Override
    public String redirectUriFor(String provider) {
        String uri = redirectUris.get(provider);
        if (uri == null) {
            throw new IllegalArgumentException("No redirect URI configured for provider: " + provider);
        }
        return uri;
    }
}
