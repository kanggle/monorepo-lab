package com.example.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, UUID> {

    Optional<AddressJpaEntity> findByIdAndUserIdAndTenantId(UUID id, UUID userId, String tenantId);

    List<AddressJpaEntity> findAllByUserIdAndTenantId(UUID userId, String tenantId);

    int countByUserIdAndTenantId(UUID userId, String tenantId);

    @Modifying
    @Query("UPDATE AddressJpaEntity a SET a.isDefault = false WHERE a.userId = :userId AND a.tenantId = :tenantId AND a.isDefault = true")
    void unmarkDefaultByUserId(@Param("userId") UUID userId, @Param("tenantId") String tenantId);
}
