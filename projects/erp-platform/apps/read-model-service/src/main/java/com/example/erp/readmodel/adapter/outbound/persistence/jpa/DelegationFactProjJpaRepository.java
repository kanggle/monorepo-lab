package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DelegationFactProjJpaRepository
        extends JpaRepository<DelegationFactProjJpaEntity, String>,
        JpaSpecificationExecutor<DelegationFactProjJpaEntity> {
}
