package com.example.auth.presentation;

import com.example.auth.application.LoginUseCase;
import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.result.LoginResult;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.presentation.dto.LoginRequest;
import com.example.auth.presentation.dto.LoginResponse;
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
public class LoginController {

    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        SessionContext sessionContext = new SessionContext(
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                httpRequest.getHeader("X-Device-Fingerprint"),
                httpRequest.getHeader("X-Geo-Country") != null
                        ? httpRequest.getHeader("X-Geo-Country") : "XX"
        );

        LoginCommand command = new LoginCommand(
                request.email(),
                request.password(),
                request.tenantId(),   // TASK-BE-229: pass optional tenant context
                sessionContext
        );

        LoginResult result = loginUseCase.execute(command);
        return ResponseEntity.ok(LoginResponse.from(result));
    }
}
