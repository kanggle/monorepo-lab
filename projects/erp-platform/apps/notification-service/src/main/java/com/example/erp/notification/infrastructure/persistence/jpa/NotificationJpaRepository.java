package com.example.erp.notification.infrastructure.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    /** Recipient-scoped detail lookup (foreign recipient → empty → 404). */
    Optional<NotificationJpaEntity> findByIdAndTenantIdAndRecipientId(
            String id, String tenantId, String recipientId);

    /** System-internal lookup (tenant-scoped, NOT recipient-scoped) — external retry render. */
    Optional<NotificationJpaEntity> findByIdAndTenantId(String id, String tenantId);

    @Query("""
            SELECT n FROM NotificationJpaEntity n
            WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId
              AND (:read IS NULL OR n.read = :read)
            ORDER BY n.createdAt DESC, n.id DESC
            """)
    Page<NotificationJpaEntity> findInbox(@Param("tenantId") String tenantId,
                                          @Param("recipientId") String recipientId,
                                          @Param("read") Boolean read,
                                          Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM NotificationJpaEntity n
            WHERE n.tenantId = :tenantId AND n.recipientId = :recipientId
              AND (:read IS NULL OR n.read = :read)
            """)
    long countInbox(@Param("tenantId") String tenantId,
                    @Param("recipientId") String recipientId,
                    @Param("read") Boolean read);
}
