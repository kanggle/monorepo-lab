package com.example.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AddressJpaRepository extends JpaRepository<AddressJpaEntity, UUID> {

    Optional<AddressJpaEntity> findByIdAndUserId(UUID id, UUID userId);

    List<AddressJpaEntity> findAllByUserId(UUID userId);

    int countByUserId(UUID userId);

    @Modifying
    @Query("UPDATE AddressJpaEntity a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    void unmarkDefaultByUserId(@Param("userId") UUID userId);
}
