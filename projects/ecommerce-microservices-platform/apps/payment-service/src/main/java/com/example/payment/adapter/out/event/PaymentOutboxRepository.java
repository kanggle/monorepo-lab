package com.example.payment.adapter.out.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for {@code payment_outbox} (TASK-BE-449, outbox v2).
 *
 * <p>Shaped for {@code SpringDataOutboxRowRepository.wrap(...)}: a
 * {@code findPending(Pageable)} returning unpublished rows in FIFO order and a
 * {@code countByPublishedAtIsNull()} for the pending-count gauge.
 */
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxEntity, UUID> {

    @Query("SELECT o FROM PaymentOutboxEntity o WHERE o.publishedAt IS NULL ORDER BY o.occurredAt ASC")
    List<PaymentOutboxEntity> findPending(Pageable pageable);

    long countByPublishedAtIsNull();
}
