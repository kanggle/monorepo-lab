package com.example.membership.presentation.exception;

import com.example.membership.application.exception.AccountNotEligibleException;
import com.example.membership.application.exception.AccountStatusUnavailableException;
import com.example.membership.application.exception.PlanNotFoundException;
import com.example.membership.application.exception.SubscriptionAlreadyActiveException;
import com.example.membership.application.exception.SubscriptionNotActiveException;
import com.example.membership.application.exception.SubscriptionNotFoundException;
import com.example.membership.application.exception.SubscriptionPermissionDeniedException;
import com.example.membership.domain.subscription.status.SubscriptionStateTransitionException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(SubscriptionAlreadyActiveException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyActive(SubscriptionAlreadyActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SUBSCRIPTION_ALREADY_ACTIVE", e.getMessage()));
    }

    @ExceptionHandler(AccountNotEligibleException.class)
    public ResponseEntity<ErrorResponse> handleNotEligible(AccountNotEligibleException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ACCOUNT_NOT_ELIGIBLE",
                        "Account is not eligible for subscription (status=" + e.getStatus() + ")"));
    }

    @ExceptionHandler(AccountStatusUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleStatusUnavailable(AccountStatusUnavailableException e) {
        log.warn("account-service unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("ACCOUNT_STATUS_UNAVAILABLE",
                        "Account status service is temporarily unavailable"));
    }

    @ExceptionHandler(SubscriptionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SubscriptionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SUBSCRIPTION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SubscriptionNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleNotActive(SubscriptionNotActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("SUBSCRIPTION_NOT_ACTIVE", e.getMessage()));
    }

    @ExceptionHandler(SubscriptionPermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermission(SubscriptionPermissionDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("PERMISSION_DENIED", e.getMessage()));
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePlan(PlanNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PLAN_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SubscriptionStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleTransition(SubscriptionStateTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("STATE_TRANSITION_INVALID", e.getMessage()));
    }
}
