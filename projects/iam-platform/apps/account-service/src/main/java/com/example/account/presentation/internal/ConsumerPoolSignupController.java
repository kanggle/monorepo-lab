package com.example.account.presentation.internal;

import com.example.account.application.command.ConsumerPoolSignupCommand;
import com.example.account.application.service.ConsumerPoolSignupUseCase;
import com.example.account.presentation.dto.request.ConsumerPoolSignupRequest;
import com.example.account.presentation.dto.response.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * TASK-MONO-772 S3 (auth-to-account.md § {@code POST /internal/consumer-pool/signups}) — the site-less pool signup
 * behind the IdP's {@code /operator-invitations/signup}. Caller: auth-service, server-side.
 *
 * <p>Authentication: the {@code /internal/**} chain — IAM {@code client_credentials} Bearer JWT with
 * {@code internal.invoke} in real profiles (the dev/test bypass authenticates tests), like
 * {@link ConsumerPoolLegacyMoveController}. No {@code X-Tenant-Id} is read: the account is born in
 * {@code consumer-pool}, always.
 */
@RestController
@RequiredArgsConstructor
public class ConsumerPoolSignupController {

    private final ConsumerPoolSignupUseCase signupUseCase;

    @PostMapping("/internal/consumer-pool/signups")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody ConsumerPoolSignupRequest request) {
        ConsumerPoolSignupCommand command = new ConsumerPoolSignupCommand(
                request.email(), request.password(), request.displayName(), request.locale(), request.timezone());
        return ResponseEntity.status(HttpStatus.CREATED).body(SignupResponse.from(signupUseCase.execute(command)));
    }
}
