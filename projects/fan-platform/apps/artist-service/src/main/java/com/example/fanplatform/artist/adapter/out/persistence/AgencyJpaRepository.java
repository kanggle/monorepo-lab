package com.example.fanplatform.artist.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface AgencyJpaRepository extends JpaRepository<AgencyJpaEntity, String> {

    Optional<AgencyJpaEntity> findByIdAndTenantId(String id, String tenantId);

    boolean existsByTenantIdAndName(String tenantId, String name);

    Page<AgencyJpaEntity> findByTenantId(String tenantId, Pageable pageable);

    List<AgencyJpaEntity> findByTenantIdAndIdIn(String tenantId, Collection<String> ids);
}
