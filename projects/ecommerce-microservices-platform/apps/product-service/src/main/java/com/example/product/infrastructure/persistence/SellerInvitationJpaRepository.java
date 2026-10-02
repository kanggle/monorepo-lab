package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.SellerInvitationStatus;
import com.example.product.infrastructure.persistence.entity.SellerInvitationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface SellerInvitationJpaRepository extends JpaRepository<SellerInvitationJpaEntity, String> {

    @Query("SELECT i FROM SellerInvitationJpaEntity i WHERE i.tenantId = :tenantId AND i.tokenHash = :tokenHash")
    Optional<SellerInvitationJpaEntity> findByTokenHash(@Param("tenantId") String tenantId,
                                                        @Param("tokenHash") String tokenHash);

    @Query("SELECT i FROM SellerInvitationJpaEntity i WHERE i.tenantId = :tenantId AND i.sellerId = :sellerId "
            + "ORDER BY i.createdAt DESC")
    List<SellerInvitationJpaEntity> findBySeller(@Param("tenantId") String tenantId, @Param("sellerId") String sellerId);

    /** Single use: only a PENDING row of this tenant moves to ACCEPTED; the loser of a race updates 0 rows. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SellerInvitationJpaEntity i SET i.status = :accepted, i.acceptedAt = :acceptedAt, "
            + "i.acceptedAccountId = :accountId "
            + "WHERE i.tenantId = :tenantId AND i.id = :id AND i.status = :pending")
    int markAccepted(@Param("tenantId") String tenantId,
                     @Param("id") String id,
                     @Param("accountId") String accountId,
                     @Param("acceptedAt") Instant acceptedAt,
                     @Param("accepted") SellerInvitationStatus accepted,
                     @Param("pending") SellerInvitationStatus pending);
}
