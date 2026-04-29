package com.wms.inbound.adapter.out.persistence.asn;

import com.wms.inbound.application.port.out.AsnNoSequencePort;
import com.wms.inbound.application.port.out.AsnPersistencePort;
import com.wms.inbound.domain.model.Asn;
import com.wms.inbound.domain.model.AsnLine;
import com.wms.inbound.domain.model.AsnSource;
import com.wms.inbound.domain.model.AsnStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AsnPersistenceAdapter implements AsnPersistencePort, AsnNoSequencePort {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AsnJpaRepository asnRepo;
    private final AsnLineJpaRepository lineRepo;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public AsnPersistenceAdapter(AsnJpaRepository asnRepo,
                                  AsnLineJpaRepository lineRepo,
                                  Clock clock) {
        this.asnRepo = asnRepo;
        this.lineRepo = lineRepo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Asn save(Asn asn) {
        AsnJpaEntity entity = asnRepo.findById(asn.getId()).orElse(null);
        if (entity == null) {
            entity = new AsnJpaEntity(asn.getId(), asn.getAsnNo(), asn.getSource().name(),
                    asn.getSupplierPartnerId(), asn.getWarehouseId(),
                    asn.getExpectedArriveDate(), asn.getNotes(),
                    asn.getStatus().name(), asn.getVersion(),
                    asn.getCreatedAt(), asn.getCreatedBy(),
                    asn.getUpdatedAt(), asn.getUpdatedBy());
        } else {
            entity.setStatus(asn.getStatus().name());
            entity.setUpdatedAt(asn.getUpdatedAt());
            entity.setUpdatedBy(asn.getUpdatedBy());
        }

        List<AsnLineJpaEntity> lineEntities = asn.getLines().stream()
                .map(l -> new AsnLineJpaEntity(l.getId(), l.getAsnId(), l.getLineNo(),
                        l.getSkuId(), l.getLotId(), l.getExpectedQty()))
                .toList();
        entity.setLines(lineEntities);

        AsnJpaEntity saved = asnRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Asn> findById(UUID id) {
        return asnRepo.findById(id).map(this::toDomainWithLines);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Asn> findByAsnNo(String asnNo) {
        return asnRepo.findByAsnNo(asnNo).map(this::toDomainWithLines);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAsnNo(String asnNo) {
        return asnRepo.existsByAsnNo(asnNo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Asn> findByWarehouseId(UUID warehouseId, AsnStatus status, int page, int size) {
        return asnRepo.findAllFiltered(status != null ? status.name() : null, warehouseId,
                PageRequest.of(page, size)).stream().map(this::toDomainWithLines).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByWarehouseId(UUID warehouseId, AsnStatus status) {
        return asnRepo.countFiltered(status != null ? status.name() : null, warehouseId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Asn> findAll(AsnStatus status, UUID warehouseId, int page, int size) {
        return asnRepo.findAllFiltered(status != null ? status.name() : null, warehouseId,
                PageRequest.of(page, size)).stream().map(this::toDomainWithLines).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll(AsnStatus status, UUID warehouseId) {
        return asnRepo.countFiltered(status != null ? status.name() : null, warehouseId);
    }

    @Override
    @Transactional
    public String nextAsnNo() {
        String date = LocalDate.now(clock).format(DATE_FMT);
        String prefix = "ASN-" + date + "-";
        Long seq = ((Number) entityManager.createNativeQuery("""
                INSERT INTO asn_no_sequence (date_key, last_seq, updated_at)
                VALUES (:date, 1, now())
                ON CONFLICT (date_key) DO UPDATE
                  SET last_seq = asn_no_sequence.last_seq + 1,
                      updated_at = now()
                RETURNING last_seq
                """)
                .setParameter("date", date)
                .getSingleResult()).longValue();
        return prefix + String.format("%04d", seq);
    }

    private Asn toDomainWithLines(AsnJpaEntity e) {
        List<AsnLine> lines = lineRepo.findByAsnIdOrderByLineNoAsc(e.getId()).stream()
                .map(AsnPersistenceAdapter::toLineDomain).toList();
        return new Asn(e.getId(), e.getAsnNo(), AsnSource.valueOf(e.getSource()),
                e.getSupplierPartnerId(), e.getWarehouseId(),
                e.getExpectedArriveDate(), e.getNotes(),
                AsnStatus.valueOf(e.getStatus()), e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(), lines);
    }

    private static Asn toDomain(AsnJpaEntity e) {
        return new Asn(e.getId(), e.getAsnNo(), AsnSource.valueOf(e.getSource()),
                e.getSupplierPartnerId(), e.getWarehouseId(),
                e.getExpectedArriveDate(), e.getNotes(),
                AsnStatus.valueOf(e.getStatus()), e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy(),
                e.getLines().stream().map(AsnPersistenceAdapter::toLineDomain).toList());
    }

    private static AsnLine toLineDomain(AsnLineJpaEntity e) {
        return new AsnLine(e.getId(), e.getAsnId(), e.getLineNo(),
                e.getSkuId(), e.getLotId(), e.getExpectedQty());
    }
}
