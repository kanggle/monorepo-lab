package com.example.auth.application.service;

import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.OAuthCallbackResult;
import com.example.auth.application.dto.OAuthLoginCommand;
import com.example.auth.application.exception.OAuthException;
import com.example.auth.application.exception.OAuthUpstreamException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.UserSignedUp;
import com.example.auth.domain.repository.OAuthStateStore;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.OAuthCallbackProperties;
import com.example.auth.domain.service.OAuthProvider;
import com.example.auth.domain.service.OAuthProvider.OAuthUserInfo;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OAuthService {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final Map<String, OAuthProvider> oauthProviders;
    private final OAuthStateStore oauthStateStore;
    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenGenerator tokenGenerator;
    private final TokenProperties tokenProperties;
    private final SessionProperties sessionProperties;
    private final UserSessionRegistry sessionRegistry;
    private final OAuthCallbackProperties oauthCallbackProperties;
    private final AuthEventPublisher eventPublisher;

    public OAuthService(List<OAuthProvider> providerList,
                        OAuthStateStore oauthStateStore,
                        UserRepository userRepository,
                        RefreshTokenStore refreshTokenStore,
                        TokenGenerator tokenGenerator,
                        TokenProperties tokenProperties,
                        SessionProperties sessionProperties,
                        UserSessionRegistry sessionRegistry,
                        OAuthCallbackProperties oauthCallbackProperties,
                        AuthEventPublisher eventPublisher) {
        this.oauthProviders = providerList.stream()
            .collect(Collectors.toMap(OAuthProvider::provider, Function.identity()));
        this.oauthStateStore = oauthStateStore;
        this.userRepository = userRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenGenerator = tokenGenerator;
        this.tokenProperties = tokenProperties;
        this.sessionProperties = sessionProperties;
        this.sessionRegistry = sessionRegistry;
        this.oauthCallbackProperties = oauthCallbackProperties;
        this.eventPublisher = eventPublisher;
    }

    public String buildAuthorizationUrl(String provider, String callbackUrl) {
        OAuthProvider oauthProvider = resolveProvider(provider);
        if (!oauthCallbackProperties.allowedCallbackUrls().contains(callbackUrl)) {
            throw new OAuthException("Invalid callbackUrl");
        }
        String state = UUID.randomUUID().toString();
        oauthStateStore.save(state, callbackUrl, STATE_TTL);
        String redirectUri = oauthCallbackProperties.redirectUriFor(provider);
        return oauthProvider.buildAuthorizationUrl(state, redirectUri);
    }

    public Optional<String> resolveCallbackUrl(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        return oauthStateStore.getAndDelete(state);
    }

    @Transactional
    public OAuthCallbackResult handleCallback(String provider, OAuthLoginCommand command) {
        OAuthProvider oauthProvider = resolveProvider(provider);
        String callbackUrl = resolveCallbackUrlOrThrow(command.state());

        OAuthUserInfo userInfo = fetchUserInfo(oauthProvider, provider, command.code(), callbackUrl);

        if (userInfo.email() == null || userInfo.email().isBlank()) {
            log.warn("{} OAuth returned user without email", provider);
            return OAuthCallbackResult.failure(callbackUrl);
        }

        User user = getOrCreateUser(userInfo, provider);

        if (!user.isActive()) {
            log.warn("OAuth login rejected: account deactivated, userId={}", user.getId());
            return OAuthCallbackResult.failure(callbackUrl);
        }

        LoginResult loginResult = issueTokensAndRegisterSession(user, provider);
        return OAuthCallbackResult.success(callbackUrl, loginResult);
    }

    private String resolveCallbackUrlOrThrow(String state) {
        return oauthStateStore.getAndDelete(state)
            .orElseThrow(() -> new OAuthException("Invalid or expired OAuth state"));
    }

    private OAuthUserInfo fetchUserInfo(OAuthProvider oauthProvider, String provider,
                                        String code, String callbackUrl) {
        try {
            String redirectUri = oauthCallbackProperties.redirectUriFor(provider);
            return oauthProvider.fetchUserInfo(code, redirectUri);
        } catch (RestClientException e) {
            log.error("Failed to fetch {} user info", provider, e);
            throw new OAuthUpstreamException(provider + " API call failed", callbackUrl, e);
        }
    }

    private User getOrCreateUser(OAuthUserInfo userInfo, String provider) {
        String normalizedEmail = userInfo.email().toLowerCase().trim();
        return userRepository.findByEmail(normalizedEmail)
            .orElseGet(() -> createOAuthUser(normalizedEmail, userInfo.name(), provider));
    }

    private LoginResult issueTokensAndRegisterSession(User user, String provider) {
        String accessToken = tokenGenerator.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();
        refreshTokenStore.save(refreshToken, user.getId(), tokenProperties.refreshTokenTtlSeconds());

        try {
            sessionRegistry.registerSession(
                user.getId(), refreshToken, sessionProperties.inactivityTimeoutSeconds());
        } catch (RuntimeException e) {
            log.error("Session registry failed during OAuth login, userId={}", user.getId(), e);
        }

        log.info("OAuth login succeeded: userId={}, provider={}", user.getId(), provider);
        return new LoginResult(accessToken, refreshToken, tokenGenerator.accessTokenTtlSeconds());
    }

    private User createOAuthUser(String email, String name, String provider) {
        String displayName = (name != null && !name.isBlank()) ? name : email.split("@")[0];
        User newUser = User.createOAuthUser(email, displayName, provider);
        User saved = userRepository.save(newUser);
        eventPublisher.publish(AuthEvent.of(new UserSignedUp(saved.getId(), email, displayName)));
        log.info("OAuth user created and UserSignedUp event published: userId={}, provider={}", saved.getId(), provider);
        return saved;
    }

    private OAuthProvider resolveProvider(String provider) {
        OAuthProvider oauthProvider = oauthProviders.get(provider);
        if (oauthProvider == null) {
            throw new OAuthException("Unsupported OAuth provider: " + provider);
        }
        return oauthProvider;
    }
}
