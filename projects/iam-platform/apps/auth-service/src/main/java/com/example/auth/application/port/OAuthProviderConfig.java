package com.example.auth.application.port;

import java.util.List;

/**
 * Framework-free view of the per-provider OAuth configuration the
 * {@code OAuthLoginUseCase} reads.
 *
 * <p>Decouples the application layer from the Spring
 * {@code @ConfigurationProperties} provider-properties type, which stays in the
 * adapter layer. {@code allowedRedirectUris} already carries the resolved server-side allowlist
 * (the adapter reproduces {@code ProviderProperties.resolveAllowedRedirectUris()}
 * exactly), so the use case performs an exact-string {@code .contains} check with
 * no normalization.
 *
 * @param clientId            OAuth client id
 * @param authUri             provider authorization endpoint
 * @param scopes              comma-separated scope list (as configured)
 * @param defaultRedirectUri  redirect_uri used when the client supplies none
 * @param allowedRedirectUris resolved exact-match redirect_uri allowlist
 */
public record OAuthProviderConfig(
        String clientId,
        String authUri,
        String scopes,
        String defaultRedirectUri,
        List<String> allowedRedirectUris
) {
}
