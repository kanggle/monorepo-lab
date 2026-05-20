package com.example.erp.masterdata.domain.jobgrade.repository;

import com.example.erp.masterdata.domain.jobgrade.JobGrade;

import java.util.List;
import java.util.Optional;

public interface JobGradeRepository {
    JobGrade save(JobGrade jobGrade);

    Optional<JobGrade> findById(String id, String tenantId);

    Optional<JobGrade> findByCode(String code, String tenantId);

    List<JobGrade> findAll(String tenantId, int page, int size);
}
