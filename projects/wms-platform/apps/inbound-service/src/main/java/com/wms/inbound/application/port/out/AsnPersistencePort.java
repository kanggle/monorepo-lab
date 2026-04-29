package com.wms.inbound.application.port.out;

import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsnPersistencePort {

    Asn save(Asn asn);

    Optional<Asn> findById(UUID id);

    Optional<Asn> findByAsnNo(String asnNo);

    boolean existsByAsnNo(String asnNo);

    List<Asn> findByWarehouseId(UUID warehouseId, AsnStatus status, int page, int size);

    long countByWarehouseId(UUID warehouseId, AsnStatus status);

    List<Asn> findAll(AsnStatus status, UUID warehouseId, int page, int size);

    long countAll(AsnStatus status, UUID warehouseId);
}
