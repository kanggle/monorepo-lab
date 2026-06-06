package com.example.security.query.internal;

import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Handles validation and error responses for query controllers.
 * Returns the standard error format defined in security-query-api.md:
 * {"code": "UPPER_SNAKE_CASE", "message": "...", "timestamp": "..."}
 *
 * Common handlers (MissingServletRequestParameterException, IllegalArgumentException,
 * MethodArgumentNotValidException, HttpMessageNotReadableException, MissingRequestHeaderException,
 * ObjectOptimisticLockingFailureException, fallback Exception) are inherited from
 * {@link CommonGlobalExceptionHandler}. Only service-specific handlers remain here.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.example.security.query.internal")
public class QueryExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.debug("Invalid parameter type: {} = {}", ex.getName(), ex.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Invalid value for parameter: " + ex.getName()));
    }
}
