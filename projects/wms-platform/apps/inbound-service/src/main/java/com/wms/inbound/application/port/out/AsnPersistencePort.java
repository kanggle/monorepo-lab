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

    /**
     * True when an <em>open</em> (not {@code CLOSED}/{@code CANCELLED}) ASN already exists for
     * this PO. Business-dedup guard for scm-sourced expectations (ADR-MONO-050 D6.2).
     */
    boolean existsOpenByPoNumber(String poNumber);

    /**
     * The most-recent <em>open</em> ASN for this PO, if any — used by the scm cancel handler
     * (ADR-MONO-050 D6.3) to locate the expectation to mark {@code CANCELLED}.
     */
    Optional<Asn> findOpenByPoNumber(String poNumber);

    List<Asn> findByWarehouseId(UUID warehouseId, AsnStatus status, int page, int size);

    long countByWarehouseId(UUID warehouseId, AsnStatus status);

    List<Asn> findAll(AsnStatus status, UUID warehouseId, int page, int size);

    long countAll(AsnStatus status, UUID warehouseId);
}
