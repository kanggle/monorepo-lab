package com.example.erp.readmodel.domain.projection.repository;

import com.example.erp.readmodel.domain.projection.CostCenterProjection;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Repository port for {@link CostCenterProjection}. */
public interface CostCenterProjectionRepository {

    Optional<CostCenterProjection> findById(String id);

    Map<String, CostCenterProjection> findAllByIds(Collection<String> ids);

    void save(CostCenterProjection projection);
}
