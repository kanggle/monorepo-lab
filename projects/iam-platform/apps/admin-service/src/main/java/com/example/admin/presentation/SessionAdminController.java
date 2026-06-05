package com.example.admin.presentation;

import com.example.admin.application.RevokeSessionCommand;
import com.example.admin.application.RevokeSessionResult;
import com.example.admin.application.SessionAdminUseCase;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.dto.RevokeSessionRequest;
import com.example.admin.presentation.dto.RevokeSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
public class SessionAdminController {

    private final SessionAdminUseCase useCase;

    @PostMapping("/{accountId}/revoke")
    @RequiresPermission(Permission.ACCOUNT_FORCE_LOGOUT)
    public ResponseEntity<RevokeSessionResponse> revoke(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) RevokeSessionRequest body) {

        String reason = headerReason != null && !headerReason.isBlank()
                ? headerReason
                : (body != null && body.reason() != null && !body.reason().isBlank() ? body.reason() : null);
        if (reason == null) throw new ReasonRequiredException();

        RevokeSessionResult r = useCase.revoke(new RevokeSessionCommand(
                accountId, reason, idempotencyKey, OperatorContextHolder.require()));
        return ResponseEntity.ok(new RevokeSessionResponse(
                r.accountId(), r.revokedSessionCount(), r.operatorId(), r.revokedAt(), r.auditId()));
    }
}
