package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CostCenterProjJpaRepository extends JpaRepository<CostCenterProjJpaEntity, String> {

    List<CostCenterProjJpaEntity> findByIdIn(Collection<String> ids);
}
