package com.example.auth.presentation.controller;

import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.OAuthCallbackResult;
import com.example.auth.application.dto.OAuthLoginCommand;
import com.example.auth.application.exception.OAuthUpstreamException;
import com.example.auth.application.service.OAuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    @GetMapping("/{provider}")
    public ResponseEntity<Void> initiateLogin(
            @PathVariable String provider,
            @RequestParam String callbackUrl) {

        String authorizationUrl = oauthService.buildAuthorizationUrl(provider, callbackUrl);

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build();
    }

    @GetMapping("/{provider}/callback")
    public void handleCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            handleOAuthError(provider, error, state, response);
            return;
        }

        if (code == null || state == null) {
            log.warn("{} OAuth callback missing parameters: code={}, state={}", provider,
                code != null ? "[present]" : null, state != null ? "[present]" : null);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth callback missing required parameters");
            return;
        }

        OAuthLoginCommand command = new OAuthLoginCommand(code, state);
        OAuthCallbackResult result;
        try {
            result = oauthService.handleCallback(provider, command);
        } catch (OAuthUpstreamException e) {
            handleUpstreamException(provider, e, response);
            return;
        }

        if (!result.success()) {
            redirectWithError(result.callbackUrl(), response);
            return;
        }

        redirectWithTokens(result.callbackUrl(), result.loginResult(), response);
    }

    private void handleOAuthError(String provider, String error, String state, HttpServletResponse response) throws IOException {
        log.warn("{} OAuth callback error: error={}, state={}", provider, error,
            state != null ? "[present]" : null);

        Optional<String> callbackUrlOpt = oauthService.resolveCallbackUrl(state);
        if (callbackUrlOpt.isPresent()) {
            redirectWithError(callbackUrlOpt.get(), response);
            return;
        }

        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth callback error");
    }

    private void handleUpstreamException(String provider, OAuthUpstreamException e, HttpServletResponse response) throws IOException {
        log.error("{} OAuth upstream error during callback", provider, e);
        if (e.getCallbackUrl() != null) {
            redirectWithError(e.getCallbackUrl(), response);
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "OAuth provider returned an error");
    }

    private void redirectWithError(String callbackUrl, HttpServletResponse response) throws IOException {
        String redirectUrl = UriComponentsBuilder.fromUriString(callbackUrl)
            .queryParam("error", "oauth_failed")
            .build()
            .toUriString();
        response.sendRedirect(redirectUrl);
    }

    private void redirectWithTokens(String callbackUrl, LoginResult loginResult, HttpServletResponse response) throws IOException {
        String redirectUrl = UriComponentsBuilder.fromUriString(callbackUrl)
            .queryParam("accessToken", loginResult.accessToken())
            .queryParam("refreshToken", loginResult.refreshToken())
            .build()
            .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
