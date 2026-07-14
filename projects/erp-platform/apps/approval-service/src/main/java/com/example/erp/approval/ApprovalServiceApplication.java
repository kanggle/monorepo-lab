package com.example.erp.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * erp-platform approval-service entry point (TASK-ERP-BE-009).
 *
 * <p>Approval Workflow bounded context, first increment. Owns the
 * {@code ApprovalRequest} aggregate + single-stage state machine
 * ({@code DRAFT → SUBMITTED → APPROVED|REJECTED|WITHDRAWN}), the no-self-approval
 * Separation-of-Duties invariant (E3 / I4), idempotent transitions (E4),
 * immutable append-only audit (E8 / A7), and a synchronous masterdata subject
 * reference-integrity check on submit (E1). Publishes the four approval
 * transition events through the transactional outbox.
 *
 * <p>Service Type: {@code rest-api}. Architecture: Hexagonal
 * (domain / application / infrastructure / presentation).
 *
 * <p><b>Runs a transactional outbox</b> (unlike {@code read-model-service}, a
 * no-outbox E5 consumer): approval-service is a producer — it emits
 * {@code erp.approval.*.v1} through its own {@code approval_outbox} table, relayed
 * by {@code ApprovalOutboxPublisher} on top of {@code libs/java-messaging}'s
 * {@code AbstractOutboxPublisher} (mirrors {@code masterdata-service}).
 * TASK-MONO-406 deleted the library's {@code OutboxAutoConfiguration}, so there is
 * no outbox auto-config left to keep or exclude.
 */
@SpringBootApplication
public class ApprovalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalServiceApplication.class, args);
    }
}
