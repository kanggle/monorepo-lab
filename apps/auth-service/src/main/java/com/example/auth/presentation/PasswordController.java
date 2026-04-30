package com.example.auth.presentation;

import com.example.auth.application.ChangePasswordUseCase;
import com.example.auth.application.command.ChangePasswordCommand;
import com.example.auth.presentation.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password management HTTP surface.
 *
 * <p>Spec: {@code specs/contracts/http/auth-api.md} ({@code PATCH /api/auth/password}).
 *
 * <p>Authentication is gateway-enforced: the gateway validates the access
 * token and forwards {@code X-Account-Id} (= {@code sub}) as a header. This
 * service does not re-validate JWTs on this endpoint, matching the pattern
 * used by {@code AccountSessionController}.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final ChangePasswordUseCase changePasswordUseCase;

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @RequestHeader("X-Account-Id") String accountId,
            @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordUseCase.execute(new ChangePasswordCommand(
                accountId,
                request.currentPassword(),
                request.newPassword()));
    }
}
