package com.example.erp.masterdata.infrastructure.persistence.jpa;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepository {

    private final DepartmentJpaRepository jpa;

    @Override
    public Department save(Department department) {
        return jpa.save(department);
    }

    @Override
    public Optional<Department> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<Department> findByCode(String code, String tenantId) {
        return jpa.findByCodeAndTenantId(code, tenantId);
    }

    @Override
    public List<Department> findAll(String tenantId, int page, int size) {
        return jpa.findAllByTenantId(tenantId, PageRequest.of(page, size));
    }

    @Override
    public List<Department> findActiveChildren(String parentId, String tenantId) {
        return jpa.findAllByParentIdAndTenantIdAndStatus(parentId, tenantId, MasterStatus.ACTIVE);
    }

    @Override
    public List<Department> ancestors(String departmentId, String tenantId) {
        // Walk parent chain bounded by tree height. Bound = 100 to defensively
        // cap pathological cycles already in storage (none should exist).
        List<Department> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Optional<Department> cur = jpa.findByIdAndTenantId(departmentId, tenantId);
        int bound = 100;
        while (cur.isPresent() && bound-- > 0) {
            Department d = cur.get();
            if (!visited.add(d.getId())) {
                break; // defensive cycle break
            }
            chain.add(d);
            String parent = d.getParentId();
            if (parent == null) break;
            cur = jpa.findByIdAndTenantId(parent, tenantId);
        }
        return chain;
    }
}
