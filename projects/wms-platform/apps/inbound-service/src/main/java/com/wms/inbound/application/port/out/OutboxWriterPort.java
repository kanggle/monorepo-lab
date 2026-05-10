package com.wms.inbound.application.port.out;

/**
 * Out-port for the outbox polling worker — retained as an empty marker to
 * avoid breaking any compile-time references held by future tasks that may
 * not yet have switched to {@link InboundEventPort}.
 *
 * @deprecated Use {@link InboundEventPort} directly.
 */
@Deprecated
public interface OutboxWriterPort {
}
