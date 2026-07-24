package com.wms.outbound.domain.exception;

/**
 * Raised by outbound-port adapters when an external system (e.g. an ERP
 * webhook target or other third-party integration) is unreachable or has
 * exhausted its retry budget.
 *
 * <p>Code {@code EXTERNAL_SERVICE_UNAVAILABLE} per
 * {@code platform/error-handling.md} (registered globally per
 * {@code rules/traits/integration-heavy.md} §Interaction with Common Rules).
 * Mapped to 503 by {@code GlobalExceptionHandler}.
 */
public class ExternalServiceUnavailableException extends OutboundDomainException {

    private final String vendor;

    public ExternalServiceUnavailableException(String vendor, String message) {
        super(message);
        this.vendor = vendor;
    }

    public ExternalServiceUnavailableException(String vendor, String message, Throwable cause) {
        super(message, cause);
        this.vendor = vendor;
    }

    public String getVendor() {
        return vendor;
    }

    @Override
    public String errorCode() {
        return "EXTERNAL_SERVICE_UNAVAILABLE";
    }
}
