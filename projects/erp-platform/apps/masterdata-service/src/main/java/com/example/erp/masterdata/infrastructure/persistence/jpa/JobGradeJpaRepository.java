package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobGradeJpaRepository extends JpaRepository<JobGrade, String> {

    Optional<JobGrade> findByIdAndTenantId(String id, String tenantId);

    Optional<JobGrade> findByCodeAndTenantId(String code, String tenantId);

    List<JobGrade> findAllByTenantId(String tenantId, Pageable pageable);
}
