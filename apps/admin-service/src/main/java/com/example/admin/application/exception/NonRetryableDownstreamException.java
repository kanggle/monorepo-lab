package com.example.admin.application.exception;

/**
 * Thrown when a downstream call returns a 4xx response. These are caller errors
 * and must not be retried — Resilience4j {@code @Retry} instances are configured
 * with this class in {@code ignoreExceptions}.
 *
 * <p>Callers should branch on {@link #getHttpStatus()} and {@link #getErrorCode()}
 * rather than parsing {@link #getMessage()}. The error code is extracted from
 * the downstream response body when present (e.g. {@code {"code":"..."}} or
 * {@code {"error":{"code":"..."}}}); it may be {@code null} if the body could
 * not be parsed.
 */
public class NonRetryableDownstreamException extends DownstreamFailureException {

    private final int httpStatus;
    private final String errorCode;

    public NonRetryableDownstreamException(String message, Throwable cause) {
        this(message, cause, 0, null);
    }

    public NonRetryableDownstreamException(String message, Throwable cause,
                                           int httpStatus, String errorCode) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
