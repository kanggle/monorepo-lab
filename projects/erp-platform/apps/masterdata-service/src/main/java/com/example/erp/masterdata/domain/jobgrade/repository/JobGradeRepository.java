package com.example.erp.masterdata.domain.jobgrade.repository;

import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;

import java.util.Optional;

public interface JobGradeRepository {
    JobGrade save(JobGrade jobGrade);

    Optional<JobGrade> findById(String id, String tenantId);

    Optional<JobGrade> findByCode(String code, String tenantId);

    /**
     * Filtered, paginated list with the TRUE total-row count
     * (masterdata-api.md § GET /job-grades + § PageMeta).
     */
    PageResult<JobGrade> findAll(String tenantId, JobGradeListFilter filter, int page, int size);
}
