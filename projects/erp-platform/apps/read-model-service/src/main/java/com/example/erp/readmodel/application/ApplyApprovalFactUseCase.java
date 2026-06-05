package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.command.ApprovalFactCommand;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactProjectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a single approval-transition event to the {@code approval_fact_proj}
 * projection (E5 read-only — latest fact only, NOT history; the authoritative
 * transition history stays with {@code approval-service}). One transaction per
 * event: dedupe check → latest-state upsert keyed by {@code approvalRequestId}
 * → record provenance.
 *
 * <p><b>Terminal-once</b> + <b>out-of-order</b> tolerance are enforced inside
 * {@link ApprovalFactProjection} (the domain object): {@code applySubmitted}
 * never reverts a terminal row; a terminal arriving before its {@code submitted}
 * upserts a row with {@code submittedAt} ABSENT (no fabrication, E5). This
 * service holds NO approval business logic (no state machine — it projects the
 * observed state). A duplicate {@code eventId} (already in
 * {@code processed_events}) is a no-op (T8).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplyApprovalFactUseCase {

    private final ApprovalFactProjectionRepository approvalRepository;
    private final EventDedupeService dedupeService;

    @Transactional
    public void apply(ApprovalFactCommand cmd) {
        if (dedupeService.isDuplicate(cmd.eventId())) {
            log.debug("Duplicate approval event skipped: eventId={} topic={}",
                    cmd.eventId(), cmd.topic());
            return;
        }
        String id = cmd.approvalRequestId();
        ApprovalFactProjection existing = approvalRepository.findById(id).orElse(null);

        if (cmd.isSubmitted()) {
            if (existing == null) {
                approvalRepository.save(ApprovalFactProjection.ofSubmitted(
                        id, cmd.subjectType(), cmd.subjectId(), cmd.approverId(),
                        cmd.submitterId(), cmd.submittedAt(), cmd.occurredAt(), cmd.eventId()));
            } else {
                // Terminal-once: applySubmitted is a no-op on a terminal row.
                existing.applySubmitted(cmd.subjectType(), cmd.subjectId(), cmd.approverId(),
                        cmd.submitterId(), cmd.submittedAt(), cmd.occurredAt(), cmd.eventId());
                approvalRepository.save(existing);
            }
        } else {
            // Terminal event (approved / rejected / withdrawn).
            if (existing == null) {
                // Out-of-order: terminal before submitted → submittedAt ABSENT (E5).
                approvalRepository.save(ApprovalFactProjection.ofTerminal(
                        id, cmd.status(), cmd.subjectType(), cmd.subjectId(), cmd.approverId(),
                        cmd.submitterId(), cmd.finalizedAt(), cmd.reason(),
                        cmd.occurredAt(), cmd.eventId()));
            } else {
                // Last-terminal-wins; never reverts to SUBMITTED.
                existing.applyTerminal(cmd.status(), cmd.subjectType(), cmd.subjectId(),
                        cmd.approverId(), cmd.submitterId(), cmd.finalizedAt(), cmd.reason(),
                        cmd.occurredAt(), cmd.eventId());
                approvalRepository.save(existing);
            }
        }
        dedupeService.markProcessed(cmd.eventId(), cmd.topic(), id);
    }
}
