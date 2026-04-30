package com.example.auth.presentation;

import com.example.auth.application.RefreshTokenUseCase;
import com.example.auth.application.command.RefreshTokenCommand;
import com.example.auth.application.result.RefreshTokenResult;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.presentation.dto.RefreshRequest;
import com.example.auth.presentation.dto.RefreshResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RefreshController {

    private final RefreshTokenUseCase refreshTokenUseCase;

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        SessionContext sessionContext = new SessionContext(
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("X-Device-Fingerprint"),
                httpRequest.getHeader("X-Geo-Country") != null
                        ? httpRequest.getHeader("X-Geo-Country") : "XX"
        );

        RefreshTokenCommand command = new RefreshTokenCommand(
                request.refreshToken(),
                sessionContext
        );

        RefreshTokenResult result = refreshTokenUseCase.execute(command);
        return ResponseEntity.ok(RefreshResponse.from(result));
    }
}
