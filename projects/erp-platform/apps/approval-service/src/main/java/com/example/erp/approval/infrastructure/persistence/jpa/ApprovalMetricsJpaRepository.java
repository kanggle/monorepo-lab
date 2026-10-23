package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Projection queries for the overview metrics (TASK-MONO-809). Windows are half-open
 * {@code [from, to)}. The participant predicate is copied verbatim from
 * {@link ApprovalRequestJpaRepository#findByParticipant}.
 */
public interface ApprovalMetricsJpaRepository extends JpaRepository<ApprovalRequest, String> {

    @Query("SELECT r.submittedAt FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND r.submittedAt >= :from AND r.submittedAt < :to")
    List<Instant> submittedAt(@Param("tenantId") String tenantId,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT r.submittedAt FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND (r.submitterId = :participantId OR r.approverId = :participantId) "
            + "AND r.submittedAt >= :from AND r.submittedAt < :to")
    List<Instant> submittedAtByParticipant(@Param("tenantId") String tenantId,
                                           @Param("participantId") String participantId,
                                           @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT r.finalizedAt FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND r.status = :status "
            + "AND r.finalizedAt >= :from AND r.finalizedAt < :to")
    List<Instant> finalizedAt(@Param("tenantId") String tenantId,
                              @Param("status") ApprovalStatus status,
                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT r.finalizedAt FROM ApprovalRequest r WHERE r.tenantId = :tenantId "
            + "AND (r.submitterId = :participantId OR r.approverId = :participantId) "
            + "AND r.status = :status "
            + "AND r.finalizedAt >= :from AND r.finalizedAt < :to")
    List<Instant> finalizedAtByParticipant(@Param("tenantId") String tenantId,
                                           @Param("participantId") String participantId,
                                           @Param("status") ApprovalStatus status,
                                           @Param("from") Instant from, @Param("to") Instant to);
}
