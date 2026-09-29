package com.example.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TASK-BE-613 (ADR-007) — picks the {@link LoginBranding} for the current browser flow.
 *
 * <p>🔴 The client is found ONLY through {@link SavedRequestTenantResolver#initiatingClient},
 * i.e. from the saved {@code /oauth2/authorize} request. Do not add a shortcut that reads a
 * {@code client_id} (or any branding value) from the current request: that would let a link
 * render a sign-in page wearing another service's name — exactly the page a phishing link
 * wants. Keeping one extraction path is what keeps this rule in one place.
 */
@Component
@RequiredArgsConstructor
public class LoginBrandingResolver {

    private final SavedRequestTenantResolver savedRequestTenantResolver;

    public LoginBranding resolve(HttpServletRequest request, HttpServletResponse response) {
        return savedRequestTenantResolver.initiatingClient(request, response)
                .map(client -> LoginBranding.from(client.getClientSettings()))
                .orElse(LoginBranding.DEFAULT);
    }
}
