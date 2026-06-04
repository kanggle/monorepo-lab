package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JobGradeProjJpaRepository extends JpaRepository<JobGradeProjJpaEntity, String> {

    List<JobGradeProjJpaEntity> findByIdIn(Collection<String> ids);
}
