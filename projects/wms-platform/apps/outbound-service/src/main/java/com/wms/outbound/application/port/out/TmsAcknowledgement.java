package com.wms.outbound.application.port.out;

/**
 * Internal model translation of a TMS push acknowledgement
 * (per integration-heavy I8). Independent of any TMS vendor wire format.
 */
public record TmsAcknowledgement(boolean success, String requestId) {
}
