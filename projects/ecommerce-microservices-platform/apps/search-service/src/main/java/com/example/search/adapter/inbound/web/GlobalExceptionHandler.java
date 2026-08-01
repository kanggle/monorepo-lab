package com.example.search.adapter.inbound.web;

import com.example.search.application.exception.SearchException;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Non-domain arms (malformed-body/404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleMissingParam}
 * and {@link #handleIllegalArgument} stay local — both answer the search-specific
 * {@code INVALID_SEARCH_REQUEST} code, not the shared handler's generic
 * {@code VALIDATION_ERROR}. Both are declared as true Java overrides (same
 * name/signature/return type as the shared method, re-declaring {@code
 * @ExceptionHandler}) rather than differently-named methods, since Spring's resolver
 * throws {@code IllegalStateException: Ambiguous @ExceptionHandler} if two methods
 * (inherited + local) map the same exact exception type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.of("ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return ErrorResponse.of("INVALID_SEARCH_REQUEST", message);
    }

    @Override
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SEARCH_REQUEST", ex.getMessage()));
    }

    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_SEARCH_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(SearchException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleSearchException(SearchException ex) {
        log.error("Search infrastructure error", ex);
        return ErrorResponse.of("SEARCH_UNAVAILABLE", "Search service is temporarily unavailable");
    }

}
