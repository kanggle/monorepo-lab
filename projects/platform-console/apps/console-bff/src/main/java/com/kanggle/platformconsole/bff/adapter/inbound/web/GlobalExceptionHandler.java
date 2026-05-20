package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.kanggle.platformconsole.bff.domain.credential.MissingCredentialException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global error envelope for console-bff controllers.
 *
 * <p>Error shape: {@code { "code": "...", "message": "...", "timestamp": "..." }}.
 * No stack traces in responses (platform/error-handling.md).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingCredentialException.class)
    public ResponseEntity<ObjectNode> handleMissingCredential(MissingCredentialException ex) {
        return error(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ObjectNode> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ObjectNode> handleGeneric(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An internal error occurred");
    }

    private ResponseEntity<ObjectNode> error(HttpStatus status, String code, String message) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.put("code", code);
        body.put("message", message != null ? message : "");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
