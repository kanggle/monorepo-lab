package com.wms.inbound.application.service;

import com.example.common.page.PageResult;
import com.wms.inbound.application.port.in.QueryAsnUseCase;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.application.result.AsnResult;
import com.wms.inbound.application.result.AsnSummaryResult;
import com.wms.inbound.domain.exception.AsnNotFoundException;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AsnQueryService implements QueryAsnUseCase {

    private final AsnPersistencePort asnPersistence;

    public AsnQueryService(AsnPersistencePort asnPersistence) {
        this.asnPersistence = asnPersistence;
    }

    @Override
    @Transactional(readOnly = true)
    public AsnResult findById(UUID id) {
        return asnPersistence.findById(id)
                .map(ReceiveAsnService::toResult)
                .orElseThrow(() -> new AsnNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AsnSummaryResult> list(String status, UUID warehouseId, int page, int size) {
        AsnStatus asnStatus = status != null ? AsnStatus.valueOf(status) : null;
        List<AsnSummaryResult> content = asnPersistence.findAll(asnStatus, warehouseId, page, size).stream()
                .map(AsnQueryService::toSummary)
                .toList();
        long total = asnPersistence.countAll(asnStatus, warehouseId);
        return new PageResult<>(content, page, size, total, totalPages(total, size));
    }

    private static int totalPages(long totalElements, int size) {
        return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    private static AsnSummaryResult toSummary(Asn asn) {
        return new AsnSummaryResult(asn.getId(), asn.getAsnNo(), asn.getSource().name(),
                asn.getSupplierPartnerId(), asn.getWarehouseId(),
                asn.getExpectedArriveDate(), asn.getStatus().name(),
                asn.getVersion(), asn.getCreatedAt());
    }
}
