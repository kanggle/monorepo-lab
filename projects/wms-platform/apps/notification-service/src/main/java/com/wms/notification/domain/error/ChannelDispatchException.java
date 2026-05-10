package com.wms.notification.domain.error;

/**
 * Sealed root for failures raised by an outbound channel adapter
 * ({@link com.wms.notification.application.port.outbound.ChannelPort})
 * during a vendor send.
 *
 * <p>Subtypes carry the application-relevant classification (configuration
 * error vs. vendor permanent failure) without leaking the vendor SDK
 * exception type into the application layer. Adapter implementations
 * translate vendor-specific errors into one of these subtypes at the
 * port boundary; the application service catches only domain types.
 *
 * <p>This is deliberately a separate root from
 * {@link NotificationDomainException}: the latter encodes
 * domain-invariant violations registered in
 * {@code platform/error-handling.md}, while {@code ChannelDispatchException}
 * encodes operational dispatch outcomes that the delivery executor maps
 * to {@code NotificationDelivery} state transitions and outbox audit codes.
 */
public abstract sealed class ChannelDispatchException extends RuntimeException
        permits ChannelNotConfiguredException, ChannelPermanentFailureException {

    protected ChannelDispatchException(String message) {
        super(message);
    }

    protected ChannelDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
