package com.example.auth.presentation;

import com.example.auth.application.OAuthLoginUseCase;
import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;
import com.example.auth.application.exception.InvalidOAuthStateException;
import com.example.auth.application.exception.OAuthEmailRequiredException;
import com.example.auth.application.exception.OAuthProviderException;
import com.example.auth.application.exception.UnsupportedProviderException;
import com.example.auth.application.result.BrowserLoginResolution;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.infrastructure.security.SavedRequestTenantResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SocialLoginBrowserController} (TASK-BE-396, ADR-006 option B).
 *
 * <p>Asserts (a) the SAS principal/details construction mirrors
 * {@code CredentialAuthenticationProvider} (HashMap details, ROLE_USER, email
 * principal), and (b) the exception → {@code /login?error=...} redirect mapping.
 */
@ExtendWith(MockitoExtension.class)
class SocialLoginBrowserControllerTest {

    @Mock
    private OAuthLoginUseCase oAuthLoginUseCase;
    @Mock
    private SavedRequestTenantResolver savedRequestTenantResolver;

    private SocialLoginBrowserController controller() {
        // Issuer base URL → the browser callback is built from this (not the request host).
        return new SocialLoginBrowserController(
                oAuthLoginUseCase, savedRequestTenantResolver, "http://iam.local");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest callbackRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/login/oauth/google/callback");
        request.setScheme("http");
        request.setServerName("iam.local");
        request.setServerPort(80);
        return request;
    }

    @Test
    @DisplayName("start: authorize URL → redirect to provider")
    void start_redirectsToProvider() {
        when(oAuthLoginUseCase.authorize(eq("google"), anyString()))
                .thenReturn(new OAuthAuthorizeResult("https://accounts.google.com/auth?x=1", "st"));

        String view = controller().startSocialLogin("google");

        assertThat(view).isEqualTo("redirect:https://accounts.google.com/auth?x=1");
        // callback URI built from the configured issuer base URL (http://iam.local)
        ArgumentCaptor<String> uri = ArgumentCaptor.forClass(String.class);
        verify(oAuthLoginUseCase).authorize(eq("google"), uri.capture());
        assertThat(uri.getValue()).isEqualTo("http://iam.local/login/oauth/google/callback");
    }

    @Test
    @DisplayName("start: unsupported provider → /login?error=unsupported_provider")
    void start_unsupportedProvider_redirectsError() {
        when(oAuthLoginUseCase.authorize(eq("naver"), anyString()))
                .thenThrow(new UnsupportedProviderException("naver"));

        String view = controller().startSocialLogin("naver");

        assertThat(view).isEqualTo("redirect:/login?error=unsupported_provider");
    }

    @Test
    @DisplayName("callback success: builds ROLE_USER principal with HashMap details, "
            + "establishes session, redirects to saved /oauth2/authorize")
    void callback_success_establishesSessionAndRedirects() {
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution(
                        "ecommerce", "B2C_CONSUMER",
                        "http://iam.local/oauth2/authorize?client_id=ecommerce-web-store-client"));
        when(oAuthLoginUseCase.resolveBrowserLogin(any(OAuthCallbackCommand.class), eq("ecommerce")))
                .thenReturn(new BrowserLoginResolution("acc-1", "user@example.com", true));

        MockHttpServletRequest request = callbackRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String view = controller().socialLoginCallback("google", "code-1", "state-1", request, response);

        assertThat(view).isEqualTo(
                "redirect:http://iam.local/oauth2/authorize?client_id=ecommerce-web-store-client");

        // SAS principal mirrors CredentialAuthenticationProvider
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(auth.getName()).isEqualTo("user@example.com");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

        // details MUST be a mutable HashMap (SecurityJackson2Modules allowlist)
        Object details = auth.getDetails();
        assertThat(details).isInstanceOf(java.util.HashMap.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) details;
        assertThat(map).containsEntry("tenant_id", "ecommerce")
                .containsEntry("tenant_type", "B2C_CONSUMER")
                .containsEntry("account_id", "acc-1");

        // command passed to the use case carries the browser callback URI + session ctx
        ArgumentCaptor<OAuthCallbackCommand> cmd = ArgumentCaptor.forClass(OAuthCallbackCommand.class);
        verify(oAuthLoginUseCase).resolveBrowserLogin(cmd.capture(), eq("ecommerce"));
        assertThat(cmd.getValue().provider()).isEqualTo("google");
        assertThat(cmd.getValue().code()).isEqualTo("code-1");
        assertThat(cmd.getValue().state()).isEqualTo("state-1");
        assertThat(cmd.getValue().redirectUri())
                .isEqualTo("http://iam.local/login/oauth/google/callback");
    }

    @Test
    @DisplayName("callback success but no saved request → redirect to /")
    void callback_noSavedRedirect_redirectsToRoot() {
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution(
                        "fan-platform", "B2C_CONSUMER", null));
        when(oAuthLoginUseCase.resolveBrowserLogin(any(OAuthCallbackCommand.class), eq("fan-platform")))
                .thenReturn(new BrowserLoginResolution("acc-2", "u2@example.com", false));

        String view = controller().socialLoginCallback("google", "c", "s",
                callbackRequest(), new MockHttpServletResponse());

        assertThat(view).isEqualTo("redirect:/");
    }

    @Test
    @DisplayName("callback: email required → /login?error=email_required, no session")
    void callback_emailRequired_redirectsError() {
        assertErrorMapping(new OAuthEmailRequiredException(), "email_required");
    }

    @Test
    @DisplayName("callback: account locked → /login?error=account_unavailable")
    void callback_accountLocked_redirectsError() {
        assertErrorMapping(new AccountLockedException(), "account_unavailable");
    }

    @Test
    @DisplayName("callback: account status → /login?error=account_unavailable")
    void callback_accountStatus_redirectsError() {
        assertErrorMapping(new AccountStatusException("DORMANT", "ACCOUNT_DORMANT"), "account_unavailable");
    }

    @Test
    @DisplayName("callback: invalid state → /login?error=invalid_state")
    void callback_invalidState_redirectsError() {
        assertErrorMapping(new InvalidOAuthStateException(), "invalid_state");
    }

    @Test
    @DisplayName("callback: provider error → /login?error=provider_error")
    void callback_providerError_redirectsError() {
        assertErrorMapping(new OAuthProviderException("boom"), "provider_error");
    }

    private void assertErrorMapping(RuntimeException thrown, String expectedError) {
        when(savedRequestTenantResolver.resolve(any(), any()))
                .thenReturn(new SavedRequestTenantResolver.Resolution(
                        "ecommerce", "B2C_CONSUMER", "http://iam.local/oauth2/authorize"));
        when(oAuthLoginUseCase.resolveBrowserLogin(any(OAuthCallbackCommand.class), anyString()))
                .thenThrow(thrown);

        String view = controller().socialLoginCallback("google", "c", "s",
                callbackRequest(), new MockHttpServletResponse());

        assertThat(view).isEqualTo("redirect:/login?error=" + expectedError);
        // no SAS session established on the error path
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
