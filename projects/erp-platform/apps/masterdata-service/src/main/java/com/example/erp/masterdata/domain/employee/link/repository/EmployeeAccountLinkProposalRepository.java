package com.example.erp.masterdata.domain.employee.link.repository;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.employee.link.EmployeeAccountLinkProposal;

import java.util.Optional;

/** Outbound port for {@link EmployeeAccountLinkProposal} (TASK-ERP-BE-044). Every method carries {@code tenantId}. */
public interface EmployeeAccountLinkProposalRepository {

    /**
     * Inserts a new {@code PENDING} proposal and flushes inside the caller's transaction, so the
     * database's «one PENDING per employee» unique index fires in the use case. A unique violation
     * (the concurrent second proposal) is translated to {@code EMPLOYEE_LINK_CONFLICT}
     * ({@code proposal_pending}) — never a 500.
     */
    EmployeeAccountLinkProposal insertPending(EmployeeAccountLinkProposal proposal);

    /** Persists a state transition of an existing proposal (optimistic-locked by {@code version}). */
    EmployeeAccountLinkProposal save(EmployeeAccountLinkProposal proposal);

    Optional<EmployeeAccountLinkProposal> findById(String id, String tenantId);

    Optional<EmployeeAccountLinkProposal> findPendingByEmployeeId(String employeeId, String tenantId);

    /** {@code PENDING} proposals addressed to {@code accountId}, newest first. */
    PageResult<EmployeeAccountLinkProposal> findPendingByAccountId(String accountId, String tenantId,
                                                                   int page, int size);

    /** Every proposal of one employee (all states), newest first. */
    PageResult<EmployeeAccountLinkProposal> findByEmployeeId(String employeeId, String tenantId,
                                                             int page, int size);
}
