package com.example.auth.application.port;

import com.example.auth.domain.oauth.OAuthProvider;

/**
 * Selects the {@link OAuthClient} implementation for a given {@link OAuthProvider}.
 *
 * <p>Application-layer abstraction over the infrastructure provider-client
 * factory so that {@code OAuthLoginUseCase} no longer depends on the concrete
 * {@code infrastructure.oauth.OAuthClientFactory}.
 */
public interface OAuthClientProvider {

    OAuthClient getClient(OAuthProvider provider);
}
