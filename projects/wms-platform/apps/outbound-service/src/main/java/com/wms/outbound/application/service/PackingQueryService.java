package com.wms.outbound.application.service;

import com.wms.outbound.application.port.in.QueryPackingUnitUseCase;
import com.wms.outbound.application.port.out.OrderPersistencePort;
import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.application.result.PackingUnitLineResult;
import com.wms.outbound.application.result.PackingUnitResult;
import com.wms.outbound.domain.model.Order;
import com.wms.outbound.domain.model.PackingUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query service backing {@link QueryPackingUnitUseCase}. Resolves a
 * packing-unit id to its parent order and returns the canonical
 * {@link PackingUnitResult} so the REST layer never imports
 * {@code PackingPersistencePort} directly (AC-04 of TASK-BE-040).
 */
@Service
public class PackingQueryService implements QueryPackingUnitUseCase {

    private final PackingPersistencePort packingPersistence;
    private final OrderPersistencePort orderPersistence;

    public PackingQueryService(PackingPersistencePort packingPersistence,
                               OrderPersistencePort orderPersistence) {
        this.packingPersistence = packingPersistence;
        this.orderPersistence = orderPersistence;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PackingUnitResult> findById(UUID packingUnitId) {
        return packingPersistence.findById(packingUnitId).map(unit -> {
            String orderStatus = orderPersistence.findById(unit.getOrderId())
                    .map(Order::getStatus)
                    .map(Enum::name)
                    .orElse(null);
            return toResult(unit, orderStatus);
        });
    }

    private static PackingUnitResult toResult(PackingUnit u, String orderStatus) {
        List<PackingUnitLineResult> lines = u.getLines().stream()
                .map(l -> new PackingUnitLineResult(
                        l.getId(), l.getOrderLineId(), l.getSkuId(), l.getLotId(), l.getQty()))
                .toList();
        return new PackingUnitResult(
                u.getId(),
                u.getOrderId(),
                u.getCartonNo(),
                u.getPackingType().name(),
                u.getWeightGrams(),
                u.getLengthMm(),
                u.getWidthMm(),
                u.getHeightMm(),
                u.getNotes(),
                u.getStatus().name(),
                lines,
                orderStatus,
                u.getVersion(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
