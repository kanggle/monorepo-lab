package com.example.erp.readmodel.adapter.outbound.persistence.jpa;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmployeeProjJpaRepository extends JpaRepository<EmployeeProjJpaEntity, String> {

    List<EmployeeProjJpaEntity> findByStatusOrderById(String status, Pageable pageable);

    long countByStatus(String status);

    List<EmployeeProjJpaEntity> findByStatusAndDepartmentIdInOrderById(
            String status, Collection<String> departmentIds, Pageable pageable);

    long countByStatusAndDepartmentIdIn(String status, Collection<String> departmentIds);
}
