package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.jobgrade.JobGrade;
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
    public List<JobGrade> findAll(String tenantId, int page, int size) {
        return jpa.findAllByTenantId(tenantId, PageRequest.of(page, size));
    }
}
