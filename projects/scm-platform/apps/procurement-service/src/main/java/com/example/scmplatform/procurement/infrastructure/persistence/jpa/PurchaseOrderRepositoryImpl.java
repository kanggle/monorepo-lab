package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.po.PurchaseOrderLine;
import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderRepository;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PurchaseOrderRepositoryImpl implements PurchaseOrderRepository {

    private final PurchaseOrderJpaRepository poJpa;
    private final PurchaseOrderLineJpaRepository lineJpa;

    @Override
    public PurchaseOrder save(PurchaseOrder po) {
        PurchaseOrder saved = poJpa.save(po);
        // Persist line collection. We delete-then-insert is costly; instead
        // compare to existing rows. v1 simplification: save all lines (JPA
        // upserts by PK) — fine because line ids are stable UUID v7s.
        for (PurchaseOrderLine line : po.linesView()) {
            lineJpa.save(line);
        }
        return saved;
    }

    @Override
    public Optional<PurchaseOrder> findById(String id, String tenantId) {
        return poJpa.findByIdAndTenantId(id, tenantId).map(this::hydrate);
    }

    @Override
    public Optional<PurchaseOrder> findByPoNumber(String poNumber, String tenantId) {
        return poJpa.findByPoNumberAndTenantId(poNumber, tenantId).map(this::hydrate);
    }

    @Override
    public Optional<PurchaseOrder> findBySourceSuggestionId(String sourceSuggestionId, String tenantId) {
        return poJpa.findBySourceSuggestionIdAndTenantId(sourceSuggestionId, tenantId).map(this::hydrate);
    }

    @Override
    public Page<PurchaseOrder> search(String tenantId, PoStatus status, String supplierId, Pageable pageable) {
        Page<PurchaseOrder> page = poJpa.search(tenantId, status, supplierId, pageable);
        return page.map(this::hydrate);
    }

    private PurchaseOrder hydrate(PurchaseOrder po) {
        List<PurchaseOrderLine> lines = lineJpa.findByPoIdOrderByLineNoAsc(po.getId());
        po.hydrateLines(lines);
        return po;
    }
}
