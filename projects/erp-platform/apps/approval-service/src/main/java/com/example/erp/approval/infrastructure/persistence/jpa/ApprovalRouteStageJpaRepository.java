package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.route.ApprovalRouteStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRouteStageJpaRepository extends JpaRepository<ApprovalRouteStage, String> {

    List<ApprovalRouteStage> findAllByRequestIdAndTenantIdOrderByStageIndexAsc(
            String requestId, String tenantId);
}
