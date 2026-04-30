package com.example.account.presentation.internal;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.result.DeleteAccountResult;
import com.example.account.application.result.StatusChangeResult;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.presentation.dto.request.InternalDeleteAccountRequest;
import com.example.account.presentation.dto.request.LockAccountRequest;
import com.example.account.presentation.dto.request.UnlockAccountRequest;
import com.example.account.presentation.dto.response.DeleteAccountResponse;
import com.example.account.presentation.dto.response.StatusChangeResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/accounts")
public class AccountLockController {

    private final AccountStatusUseCase accountStatusUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping("/{accountId}/lock")
    public ResponseEntity<StatusChangeResponse> lockAccount(
            @PathVariable String accountId,
            @Valid @RequestBody LockAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());
        String actorType = resolveActorType(reason);
        String actorId = request.operatorId();

        Map<String, Object> details = new HashMap<>();
        if (request.ruleCode() != null) details.put("ruleCode", request.ruleCode());
        if (request.riskScore() != null) details.put("riskScore", request.riskScore());
        if (request.suspiciousEventId() != null) details.put("suspiciousEventId", request.suspiciousEventId());
        if (request.ticketId() != null) details.put("ticketId", request.ticketId());

        ChangeStatusCommand command = new ChangeStatusCommand(
                accountId,
                AccountStatus.LOCKED,
                reason,
                actorType,
                actorId,
                details.isEmpty() ? null : toJson(details)
        );

        StatusChangeResult result = accountStatusUseCase.changeStatus(command);
        return ResponseEntity.ok(StatusChangeResponse.from(result));
    }

    @PostMapping("/{accountId}/unlock")
    public ResponseEntity<StatusChangeResponse> unlockAccount(
            @PathVariable String accountId,
            @Valid @RequestBody UnlockAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());

        Map<String, Object> details = new HashMap<>();
        if (request.ticketId() != null) details.put("ticketId", request.ticketId());

        ChangeStatusCommand command = new ChangeStatusCommand(
                accountId,
                AccountStatus.ACTIVE,
                reason,
                "operator",
                request.operatorId(),
                details.isEmpty() ? null : toJson(details)
        );

        StatusChangeResult result = accountStatusUseCase.changeStatus(command);
        return ResponseEntity.ok(StatusChangeResponse.from(result));
    }

    @PostMapping("/{accountId}/delete")
    public ResponseEntity<DeleteAccountResponse> deleteAccount(
            @PathVariable String accountId,
            @Valid @RequestBody InternalDeleteAccountRequest request) {
        StatusChangeReason reason = StatusChangeReason.valueOf(request.reason());

        DeleteAccountResult result = accountStatusUseCase.deleteAccount(
                accountId,
                reason,
                "operator",
                request.operatorId()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DeleteAccountResponse.from(result));
    }

    private String resolveActorType(StatusChangeReason reason) {
        return switch (reason) {
            case AUTO_DETECT, PASSWORD_FAILURE_THRESHOLD -> "system";
            case ADMIN_LOCK, ADMIN_UNLOCK -> "operator";
            default -> "system";
        };
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
