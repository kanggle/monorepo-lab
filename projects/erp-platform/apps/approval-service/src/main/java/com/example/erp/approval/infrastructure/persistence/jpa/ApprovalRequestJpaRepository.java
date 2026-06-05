package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApprovalRequestJpaRepository extends JpaRepository<ApprovalRequest, String> {

    Optional<ApprovalRequest> findByIdAndTenantId(String id, String tenantId);

    List<ApprovalRequest> findAllByTenantId(String tenantId, Pageable pageable);

    List<ApprovalRequest> findAllByTenantIdAndStatus(String tenantId, ApprovalStatus status,
                                                     Pageable pageable);

    @Query("SELECT r FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND (r.submitterId = :participantId OR r.approverId = :participantId)")
    List<ApprovalRequest> findByParticipant(@Param("tenantId") String tenantId,
                                            @Param("participantId") String participantId,
                                            Pageable pageable);

    @Query("SELECT r FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND (r.submitterId = :participantId OR r.approverId = :participantId) "
            + "AND r.status = :status")
    List<ApprovalRequest> findByParticipantAndStatus(@Param("tenantId") String tenantId,
                                                     @Param("participantId") String participantId,
                                                     @Param("status") ApprovalStatus status,
                                                     Pageable pageable);

    List<ApprovalRequest> findAllByTenantIdAndApproverIdAndStatus(
            String tenantId, String approverId, ApprovalStatus status, Pageable pageable);

    /**
     * Inbox: pending requests whose CURRENT stage's approver is {@code approverId}
     * (status ∈ {SUBMITTED, IN_REVIEW}; TASK-ERP-BE-012). {@code approver_id} is
     * denormalized = the current stage's approver, so the membership reduces to an
     * equality + status-in filter.
     */
    @Query("SELECT r FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND r.approverId = :approverId "
            + "AND r.status IN (com.example.erp.approval.domain.request.ApprovalStatus.SUBMITTED, "
            + "com.example.erp.approval.domain.request.ApprovalStatus.IN_REVIEW)")
    List<ApprovalRequest> findInboxPending(@Param("tenantId") String tenantId,
                                           @Param("approverId") String approverId,
                                           Pageable pageable);
}
