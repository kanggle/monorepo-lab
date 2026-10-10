package com.example.admin.presentation.internal;

import com.example.admin.application.OperatorInvitationAcceptResult;
import com.example.admin.application.OperatorInvitationAcceptanceUseCase;
import com.example.admin.application.OperatorInvitationPreviewResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S3 (auth-to-admin.md § preview · § accept) — the two calls the IdP acceptance page
 * ({@code /operator-invitations/accept}, auth-service) makes, server-side, on behalf of the browser.
 *
 * <p>Under the {@code @Order(0)} {@code /internal/**} resource-server chain (IAM {@code client_credentials} JWT),
 * like {@link OperatorConsoleEligibilityController}. Not operator-gated: the caller is auth-service, and the
 * «who» of an acceptance is the {@code accountId} auth-service took from its own browser session — the page never
 * reads it from a form (auth-api.md).
 *
 * <p>🔴 R4: both bodies carry the raw token. They are records with a redacting {@code toString} — Spring's
 * message converter logs {@code Read "…" to [<body.toString()>]} at DEBUG, and a {@code Map} body would print the
 * token (the S2 CI lesson). The token travels in a POST body, never a query string, so access logs never see it.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/operator-invitations")
public class OperatorInvitationAcceptanceController {

    private final OperatorInvitationAcceptanceUseCase acceptanceUseCase;

    /** {@code 200} preview · {@code 404 OPERATOR_INVITATION_NOT_FOUND} · {@code 400 VALIDATION_ERROR}. Writes nothing. */
    @PostMapping("/preview")
    public ResponseEntity<PreviewResponse> preview(@RequestBody(required = false) PreviewRequest request) {
        OperatorInvitationPreviewResult result = acceptanceUseCase.preview(request == null ? null : request.token());
        return ResponseEntity.ok(new PreviewResponse(result.tenantId(), result.tenantDisplayName(),
                result.maskedEmail(), result.roles(), result.status(), result.expired(), result.expiresAt()));
    }

    /** {@code 200} (incl. {@code alreadyAccepted}) or the refusal table of auth-to-admin.md § accept. */
    @PostMapping("/accept")
    public ResponseEntity<AcceptResponse> accept(@RequestBody(required = false) AcceptRequest request) {
        OperatorInvitationAcceptResult result = acceptanceUseCase.accept(
                request == null ? null : request.token(),
                request == null ? null : request.accountId());
        return ResponseEntity.ok(new AcceptResponse(
                result.operatorId(), result.tenantId(), result.roles(), result.alreadyAccepted()));
    }

    /** {@code {"token": "…"}} — 🔴 {@code toString} never prints the token. */
    public record PreviewRequest(String token) {
        @Override
        public String toString() {
            return "PreviewRequest[token=<redacted>]";
        }
    }

    /** {@code {"token": "…", "accountId": "…"}} — 🔴 {@code toString} never prints the token. */
    public record AcceptRequest(String token, String accountId) {
        @Override
        public String toString() {
            return "AcceptRequest[token=<redacted>, accountId=" + accountId + "]";
        }
    }

    /** {@code tenantDisplayName} stays present as {@code null} when the tenant read failed (contract shape). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PreviewResponse(String tenantId, String tenantDisplayName, String maskedEmail, List<String> roles,
                                  String status, boolean expired, Instant expiresAt) {}

    public record AcceptResponse(String operatorId, String tenantId, List<String> roles, boolean alreadyAccepted) {}
}
