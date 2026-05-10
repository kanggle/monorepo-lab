package com.wms.outbound.domain.exception;

/**
 * Raised by outbound-port adapters when an external system (e.g. TMS) is
 * unreachable or has exhausted its retry budget.
 *
 * <p>Code {@code EXTERNAL_SERVICE_UNAVAILABLE} per
 * {@code platform/error-handling.md} (registered globally per
 * {@code rules/traits/integration-heavy.md} §Interaction with Common Rules).
 *
 * <p>For the TMS path the saga handler does not let this propagate to the
 * REST caller — it transitions the saga to {@code SHIPPED_NOT_NOTIFIED} and
 * fires an alert (see {@code external-integrations.md} §2.10). This class
 * exists so the manual-retry endpoint can surface a 503 to ops when the
 * vendor is hot-down.
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
