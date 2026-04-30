package com.example.auth.presentation;

import com.example.auth.application.LogoutUseCase;
import com.example.auth.application.command.LogoutCommand;
import com.example.auth.presentation.dto.LogoutRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LogoutController {

    private final LogoutUseCase logoutUseCase;

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        logoutUseCase.execute(new LogoutCommand(request.refreshToken(), deviceId));
        return ResponseEntity.noContent().build();
    }
}
