package com.example.auth.presentation.controller;

import com.example.auth.application.dto.LoginCommand;
import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.dto.LogoutCommand;
import com.example.auth.application.dto.RefreshCommand;
import com.example.auth.application.dto.RefreshResult;
import com.example.auth.application.dto.SignupCommand;
import com.example.auth.application.dto.SignupResult;
import com.example.auth.application.service.LoginService;
import com.example.auth.application.service.LogoutService;
import com.example.auth.application.service.RefreshTokenService;
import com.example.auth.application.service.SignupService;
import com.example.auth.presentation.support.ClientIpResolver;
import com.example.auth.presentation.dto.LoginRequest;
import com.example.auth.presentation.dto.LoginResponse;
import com.example.auth.presentation.dto.LogoutRequest;
import com.example.auth.presentation.dto.RefreshRequest;
import com.example.auth.presentation.dto.RefreshResponse;
import com.example.auth.presentation.dto.SignupRequest;
import com.example.auth.presentation.dto.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;


@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@RequestBody @Valid SignupRequest request, HttpServletRequest httpRequest) {
        SignupCommand command = new SignupCommand(
            request.email(), request.password(), request.name(),
            resolveClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        SignupResult result = signupService.signup(command);
        return new SignupResponse(result.userId(), result.email(), result.name(), result.createdAt());
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request, HttpServletRequest httpRequest) {
        LoginCommand command = new LoginCommand(
            request.email(), request.password(),
            resolveClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        LoginResult result = loginService.login(command);
        return new LoginResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@RequestBody @Valid RefreshRequest request, HttpServletRequest httpRequest) {
        RefreshCommand command = new RefreshCommand(
            request.refreshToken(),
            resolveClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        RefreshResult result = refreshTokenService.refresh(command);
        return new RefreshResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody @Valid LogoutRequest request,
                       Authentication authentication,
                       @RequestHeader("Authorization") String authorizationHeader,
                       HttpServletRequest httpRequest) {
        // JwtAuthenticationFilter가 "Bearer " 형식을 검증하고 인증을 완료한 뒤에만 이 메서드에 도달함
        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            log.error("Invalid user ID in security context: {}", authentication.getName());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Authentication state error");
        }
        String accessToken = authorizationHeader.substring(7); // strip "Bearer "
        String email = authentication.getDetails() instanceof String s ? s : null;
        LogoutCommand command = new LogoutCommand(
            request.refreshToken(), accessToken, userId, email,
            resolveClientIp(httpRequest), httpRequest.getHeader("User-Agent"));
        logoutService.logout(command);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }
}
