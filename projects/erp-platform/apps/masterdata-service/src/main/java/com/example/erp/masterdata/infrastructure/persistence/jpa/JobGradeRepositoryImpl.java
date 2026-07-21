package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.jobgrade.JobGrade;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeListFilter;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JobGradeRepositoryImpl implements JobGradeRepository {

    private final JobGradeJpaRepository jpa;

    @Override
    public JobGrade save(JobGrade jobGrade) {
        return jpa.save(jobGrade);
    }

    @Override
    public Optional<JobGrade> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<JobGrade> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public PageResult<JobGrade> findAll(String tenantId, JobGradeListFilter filter, int page, int size) {
        MasterStatus status = MasterStatusFilters.toStatus(filter.active());
        List<JobGrade> content = jpa.findFiltered(tenantId, status, filter.asOf(),
                PageRequest.of(page, size));
        long total = jpa.countFiltered(tenantId, status, filter.asOf());
        return new PageResult<>(content, total);
    }
}
