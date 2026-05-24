package com.wms.notification.adapter.outbound.persistence.jpa.delivery;

import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.domain.delivery.DeliveryStatus;
import com.wms.notification.domain.delivery.NotificationDelivery;
import com.wms.notification.domain.error.IdempotencyKeyDuplicateException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryRepositoryImpl implements DeliveryRepository {

    private final NotificationDeliveryJpaRepository repository;
    private final EntityManager entityManager;

    public DeliveryRepositoryImpl(NotificationDeliveryJpaRepository repository,
                                  EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(NotificationDelivery delivery) {
        NotificationDeliveryJpaEntity entity = new NotificationDeliveryJpaEntity(
                delivery.id(),
                delivery.eventId(),
                delivery.sourceTopic(),
                delivery.channelId(),
                delivery.recipient(),
                delivery.deliveryIdempotencyKey(),
                delivery.payloadSnapshot(),
                delivery.status().name(),
                delivery.attemptCount(),
                delivery.scheduledRetryAt().orElse(null),
                delivery.lastError().orElse(null),
                delivery.version(),
                delivery.createdAt(),
                delivery.updatedAt());
        try {
            repository.save(entity);
            entityManager.flush();
        } catch (DataIntegrityViolationException duplicate) {
            throw new IdempotencyKeyDuplicateException(delivery.deliveryIdempotencyKey(), duplicate);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void update(NotificationDelivery delivery) {
        NotificationDeliveryJpaEntity managed = repository.findById(delivery.id())
                .orElseThrow(() -> new IllegalStateException("Delivery vanished: " + delivery.id()));
        applyMutableState(managed, delivery);
        repository.save(managed);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<NotificationDelivery> findById(UUID id) {
        return repository.findById(id).map(DeliveryRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Optional<NotificationDelivery> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByDeliveryIdempotencyKey(idempotencyKey)
                .map(DeliveryRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<NotificationDelivery> findByEventId(UUID eventId) {
        return repository.findByEventId(eventId).stream()
                .map(DeliveryRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<NotificationDelivery> findAndLockPendingDueForRetry(Instant now, int batchSize) {
        return repository.findPendingDueForRetry(now, PageRequest.of(0, batchSize)).stream()
                .map(DeliveryRepositoryImpl::toDomain)
                .toList();
    }

    /**
     * Copies the mutable state fields from a domain object onto a managed JPA entity.
     * Used by both {@link #save} (new entity) and {@link #update} (existing entity)
     * to eliminate the duplicated 5-field extraction.
     */
    private static void applyMutableState(NotificationDeliveryJpaEntity entity,
                                          NotificationDelivery delivery) {
        entity.apply(
                delivery.status().name(),
                delivery.attemptCount(),
                delivery.scheduledRetryAt().orElse(null),
                delivery.lastError().orElse(null),
                delivery.updatedAt());
    }

    static NotificationDelivery toDomain(NotificationDeliveryJpaEntity row) {
        return new NotificationDelivery(
                row.getId(),
                row.getEventId(),
                row.getSourceTopic(),
                row.getChannelId(),
                row.getRecipient(),
                row.getDeliveryIdempotencyKey(),
                row.getPayloadSnapshot(),
                NotificationDelivery.DEFAULT_MAX_ATTEMPTS,
                DeliveryStatus.valueOf(row.getStatus()),
                row.getAttemptCount(),
                row.getScheduledRetryAt(),
                row.getLastError(),
                row.getVersion(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
