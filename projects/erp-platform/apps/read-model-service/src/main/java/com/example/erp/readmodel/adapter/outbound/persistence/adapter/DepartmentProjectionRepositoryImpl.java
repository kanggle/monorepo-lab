package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.DepartmentProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.DepartmentProjJpaRepository;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DepartmentProjectionRepositoryImpl implements DepartmentProjectionRepository {

    private final DepartmentProjJpaRepository jpa;

    @Override
    public Optional<DepartmentProjection> findById(String id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Map<String, DepartmentProjection> findAllByIds(Collection<String> ids) {
        Map<String, DepartmentProjection> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return out;
        }
        for (DepartmentProjJpaEntity e : jpa.findByIdIn(ids)) {
            out.put(e.getId(), toDomain(e));
        }
        return out;
    }

    /**
     * Breadth-first descendant collection over {@code parent_id}, inclusive of
     * {@code rootId}. Bounded by {@code maxDepth} and a visited-set so a
     * malformed cycle still terminates (producer guarantees acyclic).
     */
    @Override
    public List<String> findSubtreeIds(String rootId, int maxDepth) {
        Set<String> result = new LinkedHashSet<>();
        if (rootId == null || rootId.isBlank()) {
            return new ArrayList<>(result);
        }
        result.add(rootId);
        Deque<String> frontier = new ArrayDeque<>();
        frontier.add(rootId);
        int depth = 0;
        while (!frontier.isEmpty() && depth < maxDepth) {
            int levelSize = frontier.size();
            for (int i = 0; i < levelSize; i++) {
                String current = frontier.poll();
                for (DepartmentProjJpaEntity child : jpa.findByParentId(current)) {
                    if (result.add(child.getId())) {
                        frontier.add(child.getId());
                    }
                }
            }
            depth++;
        }
        return new ArrayList<>(result);
    }

    @Override
    public void save(DepartmentProjection projection) {
        DepartmentProjJpaEntity e = jpa.findById(projection.id())
                .orElseGet(DepartmentProjJpaEntity::new);
        e.setId(projection.id());
        e.setCode(projection.code());
        e.setName(projection.name());
        e.setParentId(projection.parentId());
        e.setStatus(projection.status().name());
        e.setEffectiveFrom(projection.effectiveFrom());
        e.setEffectiveTo(projection.effectiveTo());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        jpa.save(e);
    }

    private DepartmentProjection toDomain(DepartmentProjJpaEntity e) {
        return DepartmentProjection.of(
                e.getId(), e.getCode(), e.getName(), e.getParentId(),
                MasterStatus.valueOf(e.getStatus()), e.getEffectiveFrom(), e.getEffectiveTo(),
                e.getLastEventAt(), e.getLastEventId());
    }
}
