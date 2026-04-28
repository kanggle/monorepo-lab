package com.wms.inventory.adapter.out.persistence.reservation;

import com.wms.inventory.application.port.out.ReservationRepository;
import com.wms.inventory.application.query.ReservationListCriteria;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.exception.DuplicateRequestException;
import com.wms.inventory.domain.model.Reservation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link Reservation}. Idempotency at the DB layer is
 * enforced by the {@code picking_request_id} unique constraint — a duplicate
 * insert surfaces as {@link DataIntegrityViolationException}, which the
 * adapter translates to {@link DuplicateRequestException}.
 */
@Component
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final ReservationJpaRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    public ReservationPersistenceAdapter(ReservationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findById(UUID id) {
        return repository.findById(id).map(ReservationPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Reservation> findByPickingRequestId(UUID pickingRequestId) {
        return repository.findByPickingRequestId(pickingRequestId)
                .map(ReservationPersistenceMapper::toDomain);
    }

    @Override
    public Reservation insert(Reservation reservation) {
        ReservationJpaEntity entity = ReservationPersistenceMapper.toEntity(reservation);
        try {
            entityManager.persist(entity);
            entityManager.flush(); // Surface uq_picking_request_id collisions early.
        } catch (DataIntegrityViolationException collision) {
            throw new DuplicateRequestException(
                    "Reservation with pickingRequestId " + reservation.pickingRequestId()
                            + " already exists");
        }
        return ReservationPersistenceMapper.toDomain(entity);
    }

    @Override
    public Reservation updateWithVersionCheck(Reservation reservation) {
        ReservationJpaEntity managed = repository.findById(reservation.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation not found for update: " + reservation.id()));
        if (managed.getVersion() != reservation.version()) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    ReservationJpaEntity.class, reservation.id());
        }
        String reason = reservation.releasedReason() == null
                ? null : reservation.releasedReason().name();
        managed.copyMutableFields(
                reservation.status().name(),
                reason,
                reservation.confirmedAt(),
                reservation.releasedAt(),
                reservation.updatedAt(),
                reservation.updatedBy());
        entityManager.flush();
        return ReservationPersistenceMapper.toDomain(managed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationView> findViewById(UUID id) {
        return repository.findById(id)
                .map(ReservationPersistenceMapper::toDomain)
                .map(ReservationView::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PageView<ReservationView> listViews(ReservationListCriteria c) {
        StringBuilder where = new StringBuilder("WHERE 1=1");
        if (c.status() != null) where.append(" AND r.status = :status");
        if (c.warehouseId() != null) where.append(" AND r.warehouseId = :warehouseId");
        if (c.pickingRequestId() != null) where.append(" AND r.pickingRequestId = :pickingRequestId");
        if (c.expiresAfter() != null) where.append(" AND r.expiresAt >= :expiresAfter");
        if (c.expiresBefore() != null) where.append(" AND r.expiresAt <= :expiresBefore");

        var dataQuery = entityManager.createQuery(
                "SELECT r FROM ReservationJpaEntity r " + where + " ORDER BY r.updatedAt DESC",
                ReservationJpaEntity.class);
        var countQuery = entityManager.createQuery(
                "SELECT COUNT(r) FROM ReservationJpaEntity r " + where, Long.class);

        bindFilters(dataQuery, countQuery, c);
        dataQuery.setFirstResult(c.page() * c.size());
        dataQuery.setMaxResults(c.size());

        List<Reservation> domainRows = dataQuery.getResultList().stream()
                .map(ReservationPersistenceMapper::toDomain).toList();
        long total = countQuery.getSingleResult();
        List<ReservationView> views = domainRows.stream().map(ReservationView::from).toList();
        return PageView.of(views, c.page(), c.size(), total, "updatedAt,desc");
    }

    private static void bindFilters(jakarta.persistence.TypedQuery<?> dataQuery,
                                    jakarta.persistence.TypedQuery<?> countQuery,
                                    ReservationListCriteria c) {
        if (c.status() != null) {
            dataQuery.setParameter("status", c.status().name());
            countQuery.setParameter("status", c.status().name());
        }
        if (c.warehouseId() != null) {
            dataQuery.setParameter("warehouseId", c.warehouseId());
            countQuery.setParameter("warehouseId", c.warehouseId());
        }
        if (c.pickingRequestId() != null) {
            dataQuery.setParameter("pickingRequestId", c.pickingRequestId());
            countQuery.setParameter("pickingRequestId", c.pickingRequestId());
        }
        if (c.expiresAfter() != null) {
            dataQuery.setParameter("expiresAfter", c.expiresAfter());
            countQuery.setParameter("expiresAfter", c.expiresAfter());
        }
        if (c.expiresBefore() != null) {
            dataQuery.setParameter("expiresBefore", c.expiresBefore());
            countQuery.setParameter("expiresBefore", c.expiresBefore());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findExpired(Instant asOf, int limit) {
        return repository.findExpired(asOf, PageRequest.of(0, limit)).stream()
                .map(ReservationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countActive() {
        return repository.countByStatus("RESERVED");
    }
}
