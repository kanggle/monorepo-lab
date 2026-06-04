package com.example.erp.readmodel.domain.projection.repository;

import com.example.erp.readmodel.domain.projection.JobGradeProjection;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Repository port for {@link JobGradeProjection}. */
public interface JobGradeProjectionRepository {

    Optional<JobGradeProjection> findById(String id);

    Map<String, JobGradeProjection> findAllByIds(Collection<String> ids);

    void save(JobGradeProjection projection);
}
