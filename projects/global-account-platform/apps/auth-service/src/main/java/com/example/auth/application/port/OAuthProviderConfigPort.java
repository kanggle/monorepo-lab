package com.example.auth.application.port;

import com.example.auth.domain.oauth.OAuthProvider;

/**
 * Supplies the per-provider {@link OAuthProviderConfig} to the application layer.
 *
 * <p>Implemented in infrastructure over the Spring {@code @ConfigurationProperties}
 * {@code OAuthProperties}; this port keeps {@code OAuthLoginUseCase} free of the
 * framework configuration type.
 */
public interface OAuthProviderConfigPort {

    OAuthProviderConfig get(OAuthProvider provider);
}
