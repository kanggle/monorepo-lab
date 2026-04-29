package com.wms.outbound.domain.model;

import com.wms.outbound.domain.exception.OrderAlreadyShippedException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound Order aggregate root.
 *
 * <p>Authoritative references:
 * {@code specs/services/outbound-service/domain-model.md} §1 and
 * {@code specs/services/outbound-service/state-machines/order-status.md}.
 *
 * <p>Lifecycle (T4):
 * <pre>
 *   RECEIVED → PICKING → PICKED → PACKING → PACKED → SHIPPED   (terminal)
 *      \________________  cancel  ________________/
 *                                                  → CANCELLED  (terminal)
 *   RECEIVED → BACKORDERED                                       (terminal)
 * </pre>
 *
 * <p>Cancellation is forbidden from {@link OrderStatus#SHIPPED}
 * ({@link OrderAlreadyShippedException}); any other forbidden transition
 * raises {@link StateTransitionInvalidException}.
 *
 * <p>Lines are immutable after {@link #startPicking} — see
 * {@code OrderLine} javadoc.
 */
public final class Order {

    private static final Set<OrderStatus> CANCELLABLE_STATUSES = EnumSet.of(
            OrderStatus.RECEIVED,
            OrderStatus.PICKING,
            OrderStatus.PICKED,
            OrderStatus.PACKING,
            OrderStatus.PACKED);

    private final UUID id;
    private final String orderNo;
    private final OrderSource source;
    private final UUID customerPartnerId;
    private final UUID warehouseId;
    private final LocalDate requiredShipDate;
    private final String notes;
    private OrderStatus status;
    private long version;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private final List<OrderLine> lines;

    public Order(UUID id,
                 String orderNo,
                 OrderSource source,
                 UUID customerPartnerId,
                 UUID warehouseId,
                 LocalDate requiredShipDate,
                 String notes,
                 OrderStatus status,
                 long version,
                 Instant createdAt,
                 String createdBy,
                 Instant updatedAt,
                 String updatedBy,
                 List<OrderLine> lines) {
        this.id = Objects.requireNonNull(id, "id");
        this.orderNo = Objects.requireNonNull(orderNo, "orderNo");
        this.source = Objects.requireNonNull(source, "source");
        this.customerPartnerId = Objects.requireNonNull(customerPartnerId, "customerPartnerId");
        this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
        this.requiredShipDate = requiredShipDate;
        this.notes = notes;
        this.status = Objects.requireNonNull(status, "status");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.updatedBy = Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one line");
        }
        this.lines = new ArrayList<>(lines);
    }

    /**
     * Domain transition: {@code RECEIVED → PICKING}. Called in the same TX as
     * order creation (saga starts immediately) and at no other point.
     */
    public void startPicking(Instant now, String actorId) {
        if (status != OrderStatus.RECEIVED) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.PICKING.name());
        }
        this.status = OrderStatus.PICKING;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void completePicking(Instant now, String actorId) {
        if (status != OrderStatus.PICKING) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.PICKED.name());
        }
        this.status = OrderStatus.PICKED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void startPacking(Instant now, String actorId) {
        if (status != OrderStatus.PICKED) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.PACKING.name());
        }
        this.status = OrderStatus.PACKING;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void completePacking(Instant now, String actorId) {
        if (status != OrderStatus.PACKING) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.PACKED.name());
        }
        this.status = OrderStatus.PACKED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void confirmShipping(Instant now, String actorId) {
        if (status != OrderStatus.PACKED) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.SHIPPED.name());
        }
        this.status = OrderStatus.SHIPPED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    /**
     * Domain transition: any non-terminal pre-ship state → {@link OrderStatus#CANCELLED}.
     *
     * <p>Throws {@link OrderAlreadyShippedException} from {@link OrderStatus#SHIPPED}
     * (returns becomes RMA in v2) and {@link StateTransitionInvalidException}
     * from other terminal states ({@link OrderStatus#CANCELLED},
     * {@link OrderStatus#BACKORDERED}).
     */
    public void cancel(String reason, Instant now, String actorId) {
        if (status == OrderStatus.SHIPPED) {
            throw new OrderAlreadyShippedException(id);
        }
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new StateTransitionInvalidException(status.name(), OrderStatus.CANCELLED.name());
        }
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    public void backorder(String reason, Instant now, String actorId) {
        if (status != OrderStatus.RECEIVED && status != OrderStatus.PICKING) {
            // BACKORDER is the reserve-failed compensation path.
            // Saga can be REQUESTED → RESERVE_FAILED while order is RECEIVED (manual webhook intake)
            // or PICKING (ReceiveOrderService advances order in same TX before reserve outcome).
            throw new StateTransitionInvalidException(status.name(), OrderStatus.BACKORDERED.name());
        }
        this.status = OrderStatus.BACKORDERED;
        this.updatedAt = now;
        this.updatedBy = actorId;
    }

    /**
     * Whether the lines list is still mutable from a use-case point of view.
     * Lines may only be added at construction; once {@link #startPicking}
     * has fired, no further mutation is permitted.
     */
    public boolean linesAreMutable() {
        return status == OrderStatus.RECEIVED;
    }

    public UUID getId() {
        return id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public OrderSource getSource() {
        return source;
    }

    public UUID getCustomerPartnerId() {
        return customerPartnerId;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public LocalDate getRequiredShipDate() {
        return requiredShipDate;
    }

    public String getNotes() {
        return notes;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }
}
