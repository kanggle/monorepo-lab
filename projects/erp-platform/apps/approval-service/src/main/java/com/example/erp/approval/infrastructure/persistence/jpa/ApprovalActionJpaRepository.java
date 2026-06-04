package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.request.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalActionJpaRepository extends JpaRepository<ApprovalAction, Long> {

    List<ApprovalAction> findAllByApprovalRequestIdAndTenantIdOrderByOccurredAtAscIdAsc(
            String approvalRequestId, String tenantId);
}
