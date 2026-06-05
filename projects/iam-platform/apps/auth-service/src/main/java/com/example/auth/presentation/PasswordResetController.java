package com.example.auth.presentation;

import com.example.auth.application.ConfirmPasswordResetUseCase;
import com.example.auth.application.RequestPasswordResetUseCase;
import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.command.RequestPasswordResetCommand;
import com.example.auth.presentation.dto.PasswordResetConfirmRequest;
import com.example.auth.presentation.dto.PasswordResetRequestRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password-reset endpoints (TASK-BE-108: request, TASK-BE-109: confirm).
 *
 * <p>Both endpoints are unauthenticated — they must be added to the public
 * matchers in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ConfirmPasswordResetUseCase confirmPasswordResetUseCase;

    /**
     * Issue a password-reset email if the address belongs to a known account.
     *
     * <p>Always returns 204, regardless of whether the email exists, so the
     * endpoint cannot be used to probe account existence.</p>
     */
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        requestPasswordResetUseCase.execute(new RequestPasswordResetCommand(request.email()));
    }

    /**
     * Confirm a password reset by submitting the previously-issued token and a
     * new password. On success the credential hash is replaced and every
     * refresh token for the account is revoked (TASK-BE-109).
     */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        confirmPasswordResetUseCase.execute(
                new ConfirmPasswordResetCommand(request.token(), request.newPassword()));
    }
}
