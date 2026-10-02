package com.example.product.infrastructure.persistence;

import com.example.product.domain.model.SellerMemberStatus;
import com.example.product.domain.model.SellerStatus;
import com.example.product.infrastructure.persistence.entity.SellerMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface SellerMemberJpaRepository extends JpaRepository<SellerMemberJpaEntity, SellerMemberJpaEntity.MemberId> {

    @Query("SELECT m FROM SellerMemberJpaEntity m WHERE m.tenantId = :tenantId AND m.sellerId = :sellerId "
            + "ORDER BY m.joinedAt ASC")
    List<SellerMemberJpaEntity> findBySeller(@Param("tenantId") String tenantId, @Param("sellerId") String sellerId);

    @Query("SELECT m FROM SellerMemberJpaEntity m WHERE m.tenantId = :tenantId AND m.sellerId = :sellerId "
            + "AND m.accountId = :accountId")
    Optional<SellerMemberJpaEntity> findOne(@Param("tenantId") String tenantId, @Param("sellerId") String sellerId,
                                            @Param("accountId") String accountId);

    /**
     * TASK-MONO-752 — «is the store SELLER role still needed by another seller?»: an ACTIVE membership of the same
     * person in an ACTIVE seller other than {@code exceptSellerId}, within the tenant.
     */
    @Query("SELECT COUNT(m) > 0 FROM SellerMemberJpaEntity m, SellerJpaEntity s "
            + "WHERE m.tenantId = :tenantId AND m.accountId = :accountId AND m.status = :memberStatus "
            + "AND m.sellerId <> :exceptSellerId "
            + "AND s.tenantId = m.tenantId AND s.sellerId = m.sellerId AND s.status = :sellerStatus")
    boolean existsActiveElsewhere(@Param("tenantId") String tenantId,
                                  @Param("accountId") String accountId,
                                  @Param("exceptSellerId") String exceptSellerId,
                                  @Param("memberStatus") SellerMemberStatus memberStatus,
                                  @Param("sellerStatus") SellerStatus sellerStatus);
}
