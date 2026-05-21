package com.example.admin.presentation;

import com.example.admin.application.ChangeMyPasswordUseCase;
import com.example.admin.application.CreateOperatorUseCase;
import com.example.admin.application.CreateOperatorUseCase.CreateOperatorResult;
import com.example.admin.application.OperatorQueryService;
import com.example.admin.application.OperatorQueryService.OperatorPage;
import com.example.admin.application.OperatorQueryService.OperatorSummary;
import com.example.admin.application.PatchOperatorRoleUseCase;
import com.example.admin.application.PatchOperatorRoleUseCase.PatchRolesResult;
import com.example.admin.application.PatchOperatorStatusUseCase;
import com.example.admin.application.PatchOperatorStatusUseCase.PatchStatusResult;
import com.example.admin.application.UpdateOwnOperatorProfileUseCase;
import com.example.admin.application.exception.InvalidRequestException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.security.OperatorContextHolder;
import com.example.admin.presentation.aspect.RequiresPermission;
import com.example.admin.presentation.aspect.SelfServiceEndpoint;
import com.example.admin.presentation.dto.ChangeMyPasswordRequest;
import com.example.admin.presentation.dto.UpdateOperatorProfileRequest;
import com.example.admin.presentation.dto.CreateOperatorRequest;
import com.example.admin.presentation.dto.CreateOperatorResponse;
import com.example.admin.presentation.dto.OperatorListResponse;
import com.example.admin.presentation.dto.OperatorSummaryResponse;
import com.example.admin.presentation.dto.PatchOperatorRolesRequest;
import com.example.admin.presentation.dto.PatchOperatorRolesResponse;
import com.example.admin.presentation.dto.PatchOperatorStatusRequest;
import com.example.admin.presentation.dto.PatchOperatorStatusResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
public class OperatorAdminController {

    private final OperatorQueryService queryService;
    private final CreateOperatorUseCase createOperatorUseCase;
    private final PatchOperatorRoleUseCase patchOperatorRoleUseCase;
    private final PatchOperatorStatusUseCase patchOperatorStatusUseCase;
    private final ChangeMyPasswordUseCase changeMyPasswordUseCase;
    private final UpdateOwnOperatorProfileUseCase updateOwnOperatorProfileUseCase;

    @GetMapping("/me")
    public ResponseEntity<OperatorSummaryResponse> currentOperator() {
        String operatorId = OperatorContextHolder.require().operatorId();
        OperatorSummary summary = queryService.getCurrentOperator(operatorId);
        return ResponseEntity.ok(toResponse(summary));
    }

    @GetMapping("/operators")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<OperatorListResponse> listOperators(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        OperatorPage result = queryService.listOperators(status, page, size);
        List<OperatorSummaryResponse> items = new ArrayList<>(result.content().size());
        for (OperatorSummary s : result.content()) {
            items.add(toResponse(s));
        }
        return ResponseEntity.ok(new OperatorListResponse(
                items,
                result.totalElements(),
                result.page(),
                result.size(),
                result.totalPages()));
    }

    @PostMapping("/operators")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<CreateOperatorResponse> createOperator(
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOperatorRequest body) {

        String reason = requireReason(headerReason);

        CreateOperatorResult result = createOperatorUseCase.createOperator(
                body.email(),
                body.displayName(),
                body.password(),
                body.roles(),
                OperatorContextHolder.require(),
                reason,
                body.tenantId());  // TASK-BE-249

        CreateOperatorResponse response = new CreateOperatorResponse(
                result.operatorId(),
                result.email(),
                result.displayName(),
                result.status(),
                result.roles(),
                result.totpEnrolled(),
                result.createdAt(),
                result.auditId(),
                result.tenantId());  // TASK-BE-249
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/operators/{operatorId}/roles")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<PatchOperatorRolesResponse> patchRoles(
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @Valid @RequestBody PatchOperatorRolesRequest body) {

        String reason = requireReason(headerReason);

        PatchRolesResult result = patchOperatorRoleUseCase.patchRoles(
                operatorId,
                body.roles(),
                OperatorContextHolder.require(),
                reason);

        return ResponseEntity.ok(new PatchOperatorRolesResponse(
                result.operatorId(), result.roles(), result.auditId()));
    }

    @PatchMapping("/operators/{operatorId}/status")
    @RequiresPermission(Permission.OPERATOR_MANAGE)
    public ResponseEntity<PatchOperatorStatusResponse> patchStatus(
            @PathVariable String operatorId,
            @RequestHeader(value = "X-Operator-Reason", required = false) String headerReason,
            @Valid @RequestBody PatchOperatorStatusRequest body) {

        String reason = requireReason(headerReason);

        PatchStatusResult result = patchOperatorStatusUseCase.patchStatus(
                operatorId,
                body.status(),
                OperatorContextHolder.require(),
                reason);

        return ResponseEntity.ok(new PatchOperatorStatusResponse(
                result.operatorId(),
                result.previousStatus(),
                result.currentStatus(),
                result.auditId()));
    }

    @PatchMapping("/operators/me/password")
    @SelfServiceEndpoint
    public ResponseEntity<Void> changeMyPassword(
            @Valid @RequestBody ChangeMyPasswordRequest body) {

        String operatorId = OperatorContextHolder.require().operatorId();
        changeMyPasswordUseCase.changeMyPassword(operatorId, body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    /**
     * TASK-BE-306 — self-serve operator profile mutation
     * ({@code PATCH /api/admin/operators/me/profile}).
     *
     * <p>Sibling of {@code me/password}: operator JWT only (no permission, no
     * {@code X-Operator-Reason}), {@code 204 No Content}. Audit row written
     * with {@code reason="<self_profile_update>"} in the same transaction as
     * the column UPDATE (audit-heavy A3 invariant).
     *
     * <p>Structural validation (key presence on both nesting levels, unknown
     * keys, value type, length, control chars, internal whitespace) is
     * enforced here so the use case body sees an already-canonicalized
     * (value, isClear) pair.
     */
    @PatchMapping("/operators/me/profile")
    @SelfServiceEndpoint
    public ResponseEntity<Void> updateMyProfile(
            @RequestBody(required = false) UpdateOperatorProfileRequest body) {

        if (body == null) {
            // Empty/missing body — Spring would normally 400 with VALIDATION_ERROR;
            // map to the canonical INVALID_REQUEST code.
            throw new InvalidRequestException("Request body is required");
        }
        UpdateOperatorProfileRequest.OperatorContextDto ctx = body.operatorContext();
        if (ctx == null) {
            // Body present but `operatorContext` key absent (e.g. {}).
            throw new InvalidRequestException(
                    "operatorContext is required");
        }
        if (ctx.hasUnknownKey()) {
            throw new InvalidRequestException(
                    "operatorContext contains unknown keys; only 'defaultAccountId' is accepted in v1");
        }
        if (!ctx.isDefaultAccountIdPresent()) {
            // Empty carrier {"operatorContext":{}} — distinct from
            // {"operatorContext":{"defaultAccountId":null}} which is the
            // explicit clear-intent encoding.
            throw new InvalidRequestException(
                    "operatorContext.defaultAccountId key is required (use null to clear)");
        }
        if (ctx.isValueTypeInvalid()) {
            throw new InvalidRequestException(
                    "operatorContext.defaultAccountId must be a JSON string or null");
        }
        String rawValue = ctx.defaultAccountId();
        String normalizedValue = validateOptionalAccountId(rawValue);

        updateOwnOperatorProfileUseCase.update(
                OperatorContextHolder.require(),
                normalizedValue);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the canonical persisted value: {@code null} when the caller
     * explicitly sent {@code null} (clear intent); the trimmed UUID string
     * when the caller sent a structurally valid identifier. Throws
     * {@link InvalidRequestException} on any structural problem (empty string,
     * whitespace-only, length > 36, internal whitespace, control characters).
     *
     * <p>The empty string ({@code ""}) is treated identically to
     * whitespace-only: rejected as 400 (TASK-BE-306 § Edge Cases — clear
     * must be {@code null}, never empty string).
     */
    private static String validateOptionalAccountId(String value) {
        if (value == null) {
            return null;
        }
        // Reject anything that's empty, whitespace-only, too long, or has
        // internal whitespace / control characters. The value is opaque
        // (GAP does NOT verify against finance-platform — TASK-BE-304
        // § Decision authority) but must be a single non-control token.
        if (value.isEmpty() || value.trim().isEmpty()) {
            throw new InvalidRequestException(
                    "operatorContext.defaultAccountId must not be blank (use null to clear)");
        }
        if (value.length() > 36) {
            throw new InvalidRequestException(
                    "operatorContext.defaultAccountId must be at most 36 characters");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                throw new InvalidRequestException(
                        "operatorContext.defaultAccountId must not contain whitespace or control characters");
            }
        }
        return value;
    }

    private static OperatorSummaryResponse toResponse(OperatorSummary summary) {
        return new OperatorSummaryResponse(
                summary.operatorId(),
                summary.email(),
                summary.displayName(),
                summary.status(),
                summary.roles(),
                summary.totpEnrolled(),
                summary.lastLoginAt(),
                summary.createdAt());
    }

    private static String requireReason(String headerReason) {
        if (headerReason == null || headerReason.isBlank()) {
            throw new ReasonRequiredException();
        }
        return headerReason;
    }
}
