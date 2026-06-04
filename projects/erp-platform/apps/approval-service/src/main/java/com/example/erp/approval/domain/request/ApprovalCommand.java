package com.example.erp.approval.domain.request;

/**
 * The mutating transition commands of the approval state machine
 * (architecture.md § State Machine transition table). {@code create} is not a
 * transition command (it produces the initial DRAFT); the four below are the
 * edges driven by {@link ApprovalStateMachine}.
 *
 * <p>Pure Java — no framework imports.
 */
public enum ApprovalCommand {
    SUBMIT,
    APPROVE,
    REJECT,
    WITHDRAW
}
