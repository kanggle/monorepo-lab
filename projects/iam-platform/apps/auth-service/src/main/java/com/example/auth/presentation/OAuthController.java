package com.example.auth.presentation;

import com.example.auth.application.OAuthLoginUseCase;
import com.example.auth.application.command.OAuthCallbackCommand;
import com.example.auth.application.result.OAuthAuthorizeResult;
import com.example.auth.application.result.OAuthLoginResult;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.presentation.dto.OAuthAuthorizeResponse;
import com.example.auth.presentation.dto.OAuthCallbackRequest;
import com.example.auth.presentation.dto.OAuthCallbackResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthLoginUseCase oAuthLoginUseCase;

    @GetMapping("/authorize")
    public ResponseEntity<OAuthAuthorizeResponse> authorize(
            @RequestParam String provider,
            @RequestParam(required = false) String redirectUri) {

        OAuthAuthorizeResult result = oAuthLoginUseCase.authorize(provider, redirectUri);
        return ResponseEntity.ok(OAuthAuthorizeResponse.from(result));
    }

    @PostMapping("/callback")
    public ResponseEntity<OAuthCallbackResponse> callback(
            @Valid @RequestBody OAuthCallbackRequest request,
            HttpServletRequest httpRequest) {

        SessionContext sessionContext = new SessionContext(
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("X-Device-Fingerprint"),
                httpRequest.getHeader("X-Geo-Country") != null
                        ? httpRequest.getHeader("X-Geo-Country") : "XX"
        );

        OAuthCallbackCommand command = new OAuthCallbackCommand(
                request.provider(),
                request.code(),
                request.state(),
                request.redirectUri(),
                sessionContext
        );

        OAuthLoginResult result = oAuthLoginUseCase.callback(command);
        return ResponseEntity.ok(OAuthCallbackResponse.from(result));
    }
}
