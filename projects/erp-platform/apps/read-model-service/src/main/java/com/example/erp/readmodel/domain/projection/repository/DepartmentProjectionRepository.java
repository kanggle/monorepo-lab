package com.example.erp.readmodel.domain.projection.repository;

import com.example.erp.readmodel.domain.projection.DepartmentProjection;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository port for {@link DepartmentProjection} (Hexagonal — domain owns the
 * interface; the JPA adapter implements it).
 */
public interface DepartmentProjectionRepository {

    Optional<DepartmentProjection> findById(String id);

    /** Batch lookup for read-time org-view assembly (avoids N+1 on a page). */
    Map<String, DepartmentProjection> findAllByIds(Collection<String> ids);

    /** All descendant department ids of {@code rootId} inclusive (subtree filter). */
    List<String> findSubtreeIds(String rootId, int maxDepth);

    void save(DepartmentProjection projection);
}
