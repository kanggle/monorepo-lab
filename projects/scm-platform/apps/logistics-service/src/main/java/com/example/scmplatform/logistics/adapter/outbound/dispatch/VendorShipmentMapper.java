package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared base for the per-vendor {@code Dispatch ↔ vendor DTO} translators (I7/I8) — the
 * <b>vendor-independent</b> half of what {@code EasyPostShipmentMapper} and
 * {@code GoodsflowShipmentMapper} used to duplicate verbatim: snapshot (de)serialisation for
 * {@code dispatch_request_dedupe}.
 *
 * <p><b>What is NOT shared.</b> {@link #toRequest(Dispatch)} and {@link #toAck(Object)} stay
 * abstract — the vendor request/response DTOs are vendor-shaped by contract
 * (external-integrations.md §1.3/§1.9 vs §2.3/§2.9) and are deliberately <b>not</b> merged
 * (TASK-SCM-BE-051 § Out of Scope). {@link #trackingCode(Object)} exposes only the one field the
 * shared dispatch template needs for its "2xx with no tracking id" guard, so the vendor DTOs still
 * never escape this package.
 *
 * <p>{@code RES} is erased at runtime, so the concrete response {@link Class} is passed explicitly
 * to the constructor rather than inferred from the type parameter.
 *
 * @param <REQ> the vendor-shaped request DTO (package-private)
 * @param <RES> the vendor-shaped response DTO (package-private)
 */
abstract class VendorShipmentMapper<REQ, RES> {

    private final ObjectMapper objectMapper;
    private final Class<RES> responseType;
    private final String vendorLabel;

    /**
     * @param objectMapper the shared Jackson mapper
     * @param responseType the concrete {@code RES} class (generic erasure — cannot be inferred)
     * @param vendorLabel  the vendor name used verbatim in snapshot ser/deser failure messages
     */
    protected VendorShipmentMapper(ObjectMapper objectMapper, Class<RES> responseType, String vendorLabel) {
        this.objectMapper = objectMapper;
        this.responseType = responseType;
        this.vendorLabel = vendorLabel;
    }

    /** Domain {@link Dispatch} → the vendor-shaped request body (vendor-specific, I8). */
    abstract REQ toRequest(Dispatch dispatch);

    /** Vendor-shaped response → the vendor-neutral {@link DispatchAck} (vendor-specific, I8). */
    abstract DispatchAck toAck(RES response);

    /**
     * The vendor's tracking identifier (EasyPost {@code tracking_code} / 굿스플로 {@code invoiceNo}
     * = 운송장번호). Read by the shared dispatch template for its contract guard only.
     */
    abstract String trackingCode(RES response);

    /** The concrete response class, for {@code RestClient.body(Class)} under erasure. */
    final Class<RES> responseType() {
        return responseType;
    }

    /** Serialise the vendor ack for the {@code dispatch_request_dedupe} snapshot column. */
    final String serialize(RES response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + vendorLabel + " response snapshot", e);
        }
    }

    /** Rebuild the ack from a cached snapshot — the no-network-call idempotency replay path (I4). */
    final DispatchAck ackFromSnapshot(String snapshot) {
        try {
            return toAck(objectMapper.readValue(snapshot, responseType));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse cached " + vendorLabel + " snapshot", e);
        }
    }
}
