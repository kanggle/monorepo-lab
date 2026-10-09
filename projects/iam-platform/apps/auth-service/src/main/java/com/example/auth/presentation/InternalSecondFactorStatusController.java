package com.example.auth.presentation;

import com.example.auth.application.SecondFactorEnrolmentStatusQuery;
import com.example.web.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * TASK-MONO-771 S5 (admin-to-auth.md § POST /internal/auth/second-factor/enrolment-status) — which of the given
 * accounts already have a CONFIRMED second factor. admin-service calls it for the tenant entry-policy pre-check
 * («이 테넌트 운영자 중 2단계 미등록 N명»), off the hot path (never at token exchange / assume).
 *
 * <p>Under {@code /internal/auth/**} — GAP {@code client_credentials} JWT with {@code internal.invoke} (the
 * test/standalone bypass authenticates slice tests). Never exposed through the public gateway. Read-only; the
 * answer is set membership only (no secret, no timestamp).
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalSecondFactorStatusController {

    private final SecondFactorEnrolmentStatusQuery query;

    @PostMapping("/second-factor/enrolment-status")
    public ResponseEntity<?> enrolmentStatus(@RequestBody(required = false) EnrolmentStatusRequest request) {
        if (request == null || request.accountIds() == null) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("VALIDATION_ERROR", "'accountIds' is required (an array, may be empty)"));
        }
        Set<String> enrolled;
        try {
            enrolled = query.enrolledAmong(request.accountIds());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage()));
        }
        return ResponseEntity.ok(new EnrolmentStatusResponse(new ArrayList<>(enrolled)));
    }

    /** Request body — {@code accountIds} required, at most 500 distinct non-blank ids. */
    public record EnrolmentStatusRequest(List<String> accountIds) {}

    /** {@code enrolledAccountIds} ⊆ request {@code accountIds}: confirmed enrolments only. */
    public record EnrolmentStatusResponse(List<String> enrolledAccountIds) {}
}
