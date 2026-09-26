package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.TokenReuseDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-604 — {@link AuthorizationServerConfig#removeBuiltInRefreshTokenProvider}, the
 * consumer the token endpoint runs over its provider list.
 *
 * <p>The end-to-end proof (a domain rejection is final at {@code POST /oauth2/token}) is the
 * Testcontainers IT ({@code OAuth2RefreshTokenIntegrationTest} {@code @Order(8)}); this pins the
 * list operation and its guard, which the IT cannot tell apart from "SAS stopped registering
 * the built-in provider on its own".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AuthorizationServerConfigRefreshProviderTest {

    @Mock private OAuth2AuthorizationService authorizationService;
    @Mock private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenReuseDetector tokenReuseDetector;
    @Mock private BulkInvalidationStore bulkInvalidationStore;
    @Mock private DeviceSessionRepository deviceSessionRepository;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private SasRefreshTokenAuthenticationProvider ours() {
        return new SasRefreshTokenAuthenticationProvider(authorizationService, tokenGenerator,
                refreshTokenRepository, tokenReuseDetector, bulkInvalidationStore,
                deviceSessionRepository, authEventPublisher, transactionManager);
    }

    private OAuth2RefreshTokenAuthenticationProvider builtIn() {
        return new OAuth2RefreshTokenAuthenticationProvider(authorizationService, tokenGenerator);
    }

    @Test
    @DisplayName("the SAS shape (ours prepended, built-in after it) → built-in removed, ours and the other grants kept")
    void removesBuiltInKeepsOursAndOtherGrants() {
        SasRefreshTokenAuthenticationProvider ours = ours();
        OAuth2AuthorizationCodeAuthenticationProvider authCode =
                new OAuth2AuthorizationCodeAuthenticationProvider(authorizationService, tokenGenerator);
        List<AuthenticationProvider> providers = new ArrayList<>(List.of(ours, authCode, builtIn()));

        AuthorizationServerConfig.removeBuiltInRefreshTokenProvider(providers);

        assertThat(providers).containsExactly(ours, authCode);
        assertThat(providers)
                .filteredOn(p -> p.supports(OAuth2RefreshTokenAuthenticationToken.class))
                .containsExactly(ours);
    }

    @Test
    @DisplayName("custom provider missing (built-in only) → startup fails; refresh would be unsupported")
    void noCustomProvider_fails() {
        List<AuthenticationProvider> providers = new ArrayList<>(List.of(builtIn()));

        assertThatThrownBy(() -> AuthorizationServerConfig.removeBuiltInRefreshTokenProvider(providers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one refresh_token provider");
    }

    @Test
    @DisplayName("a SECOND refresh-capable provider of another class → startup fails "
            + "(the fall-through would be back under a different name)")
    void secondRefreshCapableProvider_fails() {
        AuthenticationProvider lookalike = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                return null;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
        List<AuthenticationProvider> providers = new ArrayList<>(List.of(ours(), lookalike));

        assertThatThrownBy(() -> AuthorizationServerConfig.removeBuiltInRefreshTokenProvider(providers))
                .isInstanceOf(IllegalStateException.class);
    }
}
