package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;
import com.example.scmplatform.procurement.domain.asn.AsnLine;
import com.example.scmplatform.procurement.domain.asn.repository.AsnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AsnRepositoryImpl implements AsnRepository {

    private final AsnJpaRepository asnJpa;
    private final AsnLineJpaRepository lineJpa;

    @Override
    public AdvanceShipmentNotice save(AdvanceShipmentNotice asn) {
        AdvanceShipmentNotice saved = asnJpa.save(asn);
        for (AsnLine line : asn.linesView()) {
            lineJpa.save(line);
        }
        return saved;
    }

    @Override
    public Optional<AdvanceShipmentNotice> findById(String id, String tenantId) {
        return asnJpa.findByIdAndTenantId(id, tenantId).map(this::hydrate);
    }

    @Override
    public Optional<AdvanceShipmentNotice> findBySupplierAsnRef(String supplierAsnRef, String tenantId) {
        return asnJpa.findBySupplierAsnRefAndTenantId(supplierAsnRef, tenantId).map(this::hydrate);
    }

    private AdvanceShipmentNotice hydrate(AdvanceShipmentNotice asn) {
        List<AsnLine> lines = lineJpa.findByAsnId(asn.getId());
        asn.hydrateLines(lines);
        return asn;
    }
}
