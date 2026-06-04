package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DepartmentProjJpaRepository extends JpaRepository<DepartmentProjJpaEntity, String> {

    List<DepartmentProjJpaEntity> findByIdIn(Collection<String> ids);

    List<DepartmentProjJpaEntity> findByParentId(String parentId);
}
