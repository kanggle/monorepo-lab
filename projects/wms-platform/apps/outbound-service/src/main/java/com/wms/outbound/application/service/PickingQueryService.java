package com.wms.outbound.application.service;

import com.wms.outbound.application.port.in.QueryPickingRequestUseCase;
import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.application.result.PickingRequestLineResult;
import com.wms.outbound.application.result.PickingRequestResult;
import com.wms.outbound.domain.model.PickingRequest;
import com.wms.outbound.domain.model.PickingRequestLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query service backing {@link QueryPickingRequestUseCase}. The
 * controller delegates id-based and order-based lookups here so the persistence
 * out-port does not bleed into the adapter layer (AC-04 of TASK-BE-040).
 *
 * <p>TASK-BE-343: {@link #toResult(PickingRequest)} now populates {@code lines}
 * from {@link PickingRequest#getLines()} so the §2.4 by-order endpoint returns
 * the planned {@code locationId} and {@code qtyToPick} each §2.3 confirmation
 * line requires. Both existing call sites ({@code findById}, {@code findByOrderId})
 * go through {@code toResult} — additive, no behaviour change for existing callers.
 */
@Service
public class PickingQueryService implements QueryPickingRequestUseCase {

    private final PickingPersistencePort pickingPersistence;

    public PickingQueryService(PickingPersistencePort pickingPersistence) {
        this.pickingPersistence = pickingPersistence;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickingRequestResult> findById(UUID pickingRequestId) {
        return pickingPersistence.findById(pickingRequestId).map(PickingQueryService::toResult);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PickingRequestResult> findByOrderId(UUID orderId) {
        return pickingPersistence.findByOrderId(orderId).map(PickingQueryService::toResult);
    }

    private static PickingRequestResult toResult(PickingRequest p) {
        List<PickingRequestLineResult> lines = p.getLines().stream()
                .map(PickingQueryService::toLineResult)
                .toList();
        return new PickingRequestResult(
                p.getId(),
                p.getOrderId(),
                p.getSagaId(),
                p.getWarehouseId(),
                p.getStatus().name(),
                lines,
                p.getVersion(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private static PickingRequestLineResult toLineResult(PickingRequestLine l) {
        return new PickingRequestLineResult(
                l.getId(),
                l.getOrderLineId(),
                l.getSkuId(),
                l.getLotId(),
                l.getLocationId(),
                l.getQtyToPick());
    }
}
