package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ApprovalFactProjJpaRepository
        extends JpaRepository<ApprovalFactProjJpaEntity, String>,
        JpaSpecificationExecutor<ApprovalFactProjJpaEntity> {

    /** Subject-id batch lookup for read-time subject resolution (avoids N+1 on a page). */
    List<ApprovalFactProjJpaEntity> findBySubjectIdIn(Collection<String> subjectIds);
}
