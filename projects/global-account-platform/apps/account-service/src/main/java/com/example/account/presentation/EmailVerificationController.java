package com.example.account.presentation;

import com.example.account.application.result.VerifyEmailResult;
import com.example.account.application.service.SendVerificationEmailUseCase;
import com.example.account.application.service.VerifyEmailUseCase;
import com.example.account.presentation.dto.request.VerifyEmailRequest;
import com.example.account.presentation.dto.response.VerifyEmailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for the email verification flow (TASK-BE-114).
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /api/accounts/signup/verify-email} — public, token-authenticated</li>
 *   <li>{@code POST /api/accounts/signup/resend-verification-email} —
 *       authenticated via gateway-injected {@code X-Account-Id} header</li>
 * </ul>
 *
 * <p>The verify endpoint is left {@code permitAll()}-equivalent in
 * {@code SecurityConfig} — the token in the body is itself the authentication
 * mechanism (mirrors the password-reset confirm path in auth-service).</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/accounts/signup")
public class EmailVerificationController {

    private final VerifyEmailUseCase verifyEmailUseCase;
    private final SendVerificationEmailUseCase sendVerificationEmailUseCase;

    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        VerifyEmailResult result =
                verifyEmailUseCase.execute(request.token());
        return ResponseEntity.ok(VerifyEmailResponse.from(result));
    }

    @PostMapping("/resend-verification-email")
    public ResponseEntity<Void> resendVerificationEmail(
            @RequestHeader("X-Account-Id") String accountId) {
        sendVerificationEmailUseCase.execute(accountId);
        return ResponseEntity.noContent().build();
    }
}
