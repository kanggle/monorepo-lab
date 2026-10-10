package com.example.account.presentation.internal;

import com.example.account.application.command.SendOperatorInvitationMailCommand;
import com.example.account.application.result.VerifiedEmailMatchResult;
import com.example.account.application.service.SendOperatorInvitationMailUseCase;
import com.example.account.application.service.VerifiedEmailMatchUseCase;
import com.example.account.presentation.dto.request.OperatorInvitationMailRequest;
import com.example.account.presentation.dto.request.VerifiedEmailMatchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6) — the two account-service endpoints the operator invitation needs
 * (admin-to-account.md). Caller: admin-service only, with its IAM {@code client_credentials} JWT — the existing
 * {@code /internal/**} chain ({@code SecurityConfig}). Neither path is tenant-scoped ({@code X-Tenant-Id} is not
 * read): the match finds the account under {@code consumer-pool} only, and the mail carries its tenant in the body.
 *
 * <ul>
 *   <li>{@code POST /internal/accounts/{accountId}/verified-email:match} — read-only verdict
 *       ({@link VerifiedEmailMatchUseCase}).</li>
 *   <li>{@code POST /internal/notifications/operator-invitation} — sends one mail, stores nothing
 *       ({@link SendOperatorInvitationMailUseCase}).</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class OperatorInvitationSupportController {

    private final VerifiedEmailMatchUseCase matchUseCase;
    private final SendOperatorInvitationMailUseCase mailUseCase;

    @PostMapping("/internal/accounts/{accountId}/verified-email:match")
    public ResponseEntity<VerifiedEmailMatchResponse> match(
            @PathVariable String accountId,
            @Valid @RequestBody VerifiedEmailMatchRequest request) {
        VerifiedEmailMatchResult result = matchUseCase.match(accountId, request.expectedEmail());
        return ResponseEntity.ok(new VerifiedEmailMatchResponse(result.accountId(), result.emailVerifiedAt()));
    }

    @PostMapping("/internal/notifications/operator-invitation")
    public ResponseEntity<Void> sendOperatorInvitation(@Valid @RequestBody OperatorInvitationMailRequest request) {
        mailUseCase.send(new SendOperatorInvitationMailCommand(
                request.to(), request.token(), request.tenantId(), request.inviterDisplayName(), request.expiresAt()));
        return ResponseEntity.noContent().build();
    }

    /** {@code 200} body of the match — admin-to-account.md «Response 200». */
    public record VerifiedEmailMatchResponse(String accountId, Instant emailVerifiedAt) {
    }
}
