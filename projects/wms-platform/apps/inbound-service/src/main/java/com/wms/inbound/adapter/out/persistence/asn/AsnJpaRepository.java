package com.wms.inbound.adapter.out.persistence.asn;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AsnJpaRepository extends JpaRepository<AsnJpaEntity, UUID> {

    Optional<AsnJpaEntity> findByAsnNo(String asnNo);

    boolean existsByAsnNo(String asnNo);

    @Query("SELECT a FROM AsnJpaEntity a WHERE (:status IS NULL OR a.status = :status) AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId) ORDER BY a.createdAt DESC")
    java.util.List<AsnJpaEntity> findAllFiltered(
            @Param("status") String status,
            @Param("warehouseId") UUID warehouseId,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(a) FROM AsnJpaEntity a WHERE (:status IS NULL OR a.status = :status) AND (:warehouseId IS NULL OR a.warehouseId = :warehouseId)")
    long countFiltered(
            @Param("status") String status,
            @Param("warehouseId") UUID warehouseId);

    // ADR-MONO-050 D6.2 — an "open" expectation is any ASN for this PO not yet terminal.
    @Query("SELECT COUNT(a) > 0 FROM AsnJpaEntity a WHERE a.poNumber = :poNumber AND a.status NOT IN ('CLOSED', 'CANCELLED')")
    boolean existsOpenByPoNumber(@Param("poNumber") String poNumber);

    @Query("SELECT a FROM AsnJpaEntity a WHERE a.poNumber = :poNumber AND a.status NOT IN ('CLOSED', 'CANCELLED') ORDER BY a.createdAt DESC")
    java.util.List<AsnJpaEntity> findOpenByPoNumber(
            @Param("poNumber") String poNumber,
            org.springframework.data.domain.Pageable pageable);
}
