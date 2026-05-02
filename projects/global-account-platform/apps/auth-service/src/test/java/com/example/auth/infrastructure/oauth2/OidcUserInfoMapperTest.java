package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.result.AccountProfileResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OidcUserInfoMapper}.
 *
 * <p>Verifies that the mapper correctly builds OIDC {@code /oauth2/userinfo} payloads
 * from account-service profile data, and that it behaves gracefully when the profile
 * is not found or account-service is unavailable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OidcUserInfoMapperTest {

    private OidcUserInfoMapper mapper;

    @Mock
    private AccountServicePort accountServicePort;

    @Mock
    private OidcUserInfoAuthenticationContext context;

    @Mock
    private OidcUserInfoAuthenticationToken authentication;

    @Mock
    private Authentication principal;

    @BeforeEach
    void setUp() {
        mapper = new OidcUserInfoMapper(accountServicePort);
    }

    // -----------------------------------------------------------------------
    // Happy path — full profile available
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("full profile available → all standard OIDC claims + tenant_id / tenant_type")
    void apply_fullProfile_mapsAllClaims() {
        String accountId = "acc-001";
        AccountProfileResult profile = new AccountProfileResult(
                accountId,
                "user@example.com",
                true,
                "Hong Gildong",
                "gildongh",
                "ko-KR",
                "fan-platform",
                "B2C"
        );

        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(accountId);
        when(accountServicePort.getAccountProfile(accountId)).thenReturn(Optional.of(profile));

        OidcUserInfo userInfo = mapper.apply(context);

        assertThat(userInfo.getClaims()).containsEntry("sub", accountId);
        assertThat(userInfo.getClaims()).containsEntry("email", "user@example.com");
        assertThat(userInfo.getClaims()).containsEntry("email_verified", true);
        assertThat(userInfo.getClaims()).containsEntry("name", "Hong Gildong");
        assertThat(userInfo.getClaims()).containsEntry("preferred_username", "gildongh");
        assertThat(userInfo.getClaims()).containsEntry("locale", "ko-KR");
        assertThat(userInfo.getClaims()).containsEntry("tenant_id", "fan-platform");
        assertThat(userInfo.getClaims()).containsEntry("tenant_type", "B2C");
    }

    // -----------------------------------------------------------------------
    // Partial profile — nullable fields omitted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("partial profile (no displayName, no locale) → absent claims not included")
    void apply_partialProfile_omitsNullClaims() {
        String accountId = "acc-002";
        AccountProfileResult profile = new AccountProfileResult(
                accountId,
                "partial@example.com",
                false,
                null,   // displayName absent
                null,   // preferredUsername absent
                null,   // locale absent
                "fan-platform",
                "B2C"
        );

        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(accountId);
        when(accountServicePort.getAccountProfile(accountId)).thenReturn(Optional.of(profile));

        OidcUserInfo userInfo = mapper.apply(context);

        assertThat(userInfo.getClaims()).containsKey("sub");
        assertThat(userInfo.getClaims()).containsKey("email");
        assertThat(userInfo.getClaims()).doesNotContainKey("name");
        assertThat(userInfo.getClaims()).doesNotContainKey("preferred_username");
        assertThat(userInfo.getClaims()).doesNotContainKey("locale");
    }

    // -----------------------------------------------------------------------
    // Account not found — sub-only response
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("account not found (empty Optional) → sub-only userinfo (OIDC-compliant)")
    void apply_accountNotFound_returnsSubOnly() {
        String accountId = "acc-999";

        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(accountId);
        when(accountServicePort.getAccountProfile(accountId)).thenReturn(Optional.empty());

        OidcUserInfo userInfo = mapper.apply(context);

        assertThat(userInfo.getClaims()).containsOnlyKeys("sub");
        assertThat(userInfo.getClaims()).containsEntry("sub", accountId);
    }

    // -----------------------------------------------------------------------
    // Account-service unavailable — fail-open (sub only, no 503)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("account-service unavailable → fail-open: returns sub-only (no exception thrown)")
    void apply_accountServiceUnavailable_failOpen() {
        String accountId = "acc-003";

        when(context.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn(accountId);
        when(accountServicePort.getAccountProfile(accountId))
                .thenThrow(new AccountServiceUnavailableException("timeout", null));

        // Must NOT throw — fail-open behaviour
        OidcUserInfo userInfo = mapper.apply(context);

        assertThat(userInfo.getClaims()).containsEntry("sub", accountId);
        // No other claims since the service was unavailable
        assertThat(userInfo.getClaims()).containsOnlyKeys("sub");
    }
}
