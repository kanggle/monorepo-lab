package com.example.web.exception;

import com.example.web.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
public abstract class CommonGlobalExceptionHandler {

    /**
     * HTTP status this handler answers request-validation failures with — the
     * {@code @Valid} constraint-violation arm ({@link #handleValidation}) and the
     * {@link IllegalArgumentException} arm ({@link #handleIllegalArgument}).
     *
     * <p>Defaults to {@code 400 Bad Request}, matching
     * {@code platform/error-handling.md § HTTP Status Code Mapping}. A service whose
     * published contract answers {@code 422 Unprocessable Entity} for the same case
     * overrides this once instead of re-implementing both arms:
     *
     * <pre>{@code
     * @Override
     * protected HttpStatus validationFailureStatus() {
     *     return HttpStatus.UNPROCESSABLE_ENTITY;
     * }
     * }</pre>
     *
     * <p>Added by ADR-MONO-058 § D2, which requires the shared handler to expose this
     * mapping rather than force one status on every adopter (fan-platform's three HTTP
     * contracts publish 422 for {@code @Valid} failures; the iam-platform adopters
     * publish 400). It is deliberately a {@code protected} method and not a
     * configuration property: an error status is a per-service published contract, not
     * a per-deployment knob.
     *
     * <p>Scope note — this hook covers only the two arms named above. The
     * {@code HttpMessageNotReadableException} / missing-header / missing-parameter arms
     * stay at 400 for every adopter; a service that publishes something else there
     * overrides that single handler method directly.
     */
    protected HttpStatus validationFailureStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(validationFailureStatus())
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Missing required parameter: " + e.getParameterName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(validationFailureStatus())
                .body(ErrorResponse.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONFLICT", "Concurrent modification detected. Please retry."));
    }

    /**
     * Unmapped request path — Spring 6.1+ throws {@link NoResourceFoundException} when no
     * controller mapping and no static resource matches the request. Semantically this is a
     * 404, but without a dedicated handler the catch-all {@link #handleGeneral(Exception)}
     * below would swallow it into a 500. A more specific @ExceptionHandler wins by exception
     * type specificity, so this maps the not-found case to a proper 404 regardless of
     * declaration order.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "The requested resource was not found"));
    }

    /**
     * Unmapped request path on services configured with
     * {@code spring.mvc.throw-exception-if-no-handler-found=true} (and/or static resource
     * mapping disabled), where Spring raises {@link NoHandlerFoundException} instead of
     * {@link NoResourceFoundException}. Same 404 semantics.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "The requested resource was not found"));
    }

    /**
     * Wrong HTTP method on a matched path — Spring throws
     * {@link HttpRequestMethodNotSupportedException}. Without a dedicated handler the catch-all
     * {@link #handleGeneral(Exception)} swallows it into a 500; semantically it is a client
     * error (405). Unlike {@link NoResourceFoundException} (404), this is thrown after the path
     * matches a controller, so it fires even for unauthenticated requests. Emits the RFC 7231
     * §6.5.5 {@code Allow} header listing the supported methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Set<HttpMethod> supported = e.getSupportedHttpMethods();
        if (supported != null && !supported.isEmpty()) {
            builder.allow(supported.toArray(new HttpMethod[0]));
        }
        return builder.body(ErrorResponse.of("METHOD_NOT_ALLOWED",
                "HTTP method not supported for this endpoint"));
    }

    /**
     * Unsupported request {@code Content-Type} on a matched path — Spring throws
     * {@link HttpMediaTypeNotSupportedException}. Same catch-all-swallow-into-500 defect as
     * {@link #handleMethodNotSupported}; semantically a 415.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE",
                        "Request Content-Type is not supported by this endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
